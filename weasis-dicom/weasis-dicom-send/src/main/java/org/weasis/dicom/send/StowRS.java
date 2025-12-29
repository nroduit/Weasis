/*
 * Copyright (c) 2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.send;

import com.github.scribejava.core.httpclient.multipart.MultipartPayload;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.parsers.ParserConfigurationException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.net.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.net.HttpStream;
import org.weasis.core.api.net.HttpUtils;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.BodySupplier;
import org.weasis.core.api.net.auth.FileBodyPartPayload;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.param.DicomProgress;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.web.ContentType;
import org.weasis.dicom.web.DicomStowRS;
import org.weasis.dicom.web.MultipartConstants;
import org.xml.sax.SAXException;

public class StowRS extends DicomStowRS {
  private static final Logger LOGGER = LoggerFactory.getLogger(StowRS.class);

  /**
   * @param requestURL the URL of the STOW service
   * @param contentType the value of the type in the Content-Type HTTP property
   * @param agentName the value of the User-Agent HTTP property
   * @param headers some additional header properties.
   */
  public StowRS(
      String requestURL, ContentType contentType, String agentName, Map<String, String> headers) {
    super(requestURL, contentType, agentName, headers);
  }

  private OAuthRequest prepareAuthConnection(List<String> filesOrFolders, boolean recursive) {
    OAuthRequest authRequest = new OAuthRequest(Verb.POST, getRequestURL());
    MultipartPayload multipart = getMultipartPayload(filesOrFolders, recursive);
    authRequest.setMultipartPayload(multipart);
    return authRequest;
  }

  private MultipartPayload getMultipartPayload(List<String> filesOrFolders, boolean recursive) {
    Map<String, String> headers = new HashMap<>();
    headers.put(
        "Content-Type", // NON-NLS
        "multipart/related;type=\"" // NON-NLS
            + ContentType.APPLICATION_DICOM.getType()
            + "\";boundary=" // NON-NLS
            + DEFAULT_BOUNDARY);
    headers.put("Accept", MultipartConstants.DicomContentType.XML.toString()); // NON-NLS

    MultipartPayload multipart = new MultipartPayload(DEFAULT_BOUNDARY, headers);

    for (String entry : filesOrFolders) {
      File file = new File(entry);
      if (file.isDirectory()) {
        List<Path> fileList = new ArrayList<>();
        FileUtil.getAllFilesInDirectory(file.toPath(), fileList, recursive);
        for (Path f : fileList) {
          addMultipartFile(multipart, f.toFile());
        }
      } else {
        addMultipartFile(multipart, file);
      }
    }
    return multipart;
  }

  private void addMultipartFile(MultipartPayload multipart, File file) {
    multipart.addBodyPart(
        new FileBodyPartPayload(
            getContentType().getType(),
            new BodySupplier<>() {
              @Override
              public InputStream get() throws IOException {
                return new FileInputStream(file);
              }

              @Override
              public long length() {
                return file.length();
              }
            },
            null));
  }

  public DicomState uploadDicom(
      List<String> filesOrFolders, boolean recursive, AuthMethod authMethod) {
    DicomState state = new DicomState(new DicomProgress());
    Attributes error = null;
    int nbFile = 0;

    String url = getRequestURL();
    OAuthRequest authRequest = prepareAuthConnection(filesOrFolders, recursive);
    nbFile = authRequest.getMultipartPayload().getBodyParts().size();

    try (HttpStream httpCon =
        HttpUtils.getHttpResponse(
            url, new URLParameters(getHeaders(), true), authMethod, authRequest)) {
      int code = httpCon.getResponseCode();
      if (code >= HttpURLConnection.HTTP_OK && code < HttpURLConnection.HTTP_BAD_REQUEST) {
        error = getResponseOutput(httpCon);
      } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED && authMethod != null) {
        authMethod.resetToken();
        authMethod.getToken();
      }
      return buildErrorMessage(error, state, nbFile);
    } catch (Exception e) {
      LOGGER.error("STOW-RS: error when posting data", e);
      return DicomState.buildMessage(state, e.getMessage(), null);
    }
  }

  private DicomState buildErrorMessage(Attributes error, DicomState state, int nbFile) {
    String message;
    if (error == null) {
      state.setStatus(Status.Success);
      message = "all the files has been transferred"; // NON-NLS
    } else {
      message = "one or more files has not been transferred"; // NON-NLS
      state.setStatus(Status.OneOrMoreFailures);
      DicomProgress p = state.getProgress();
      if (p != null) {
        Sequence seq = error.getSequence(Tag.FailedSOPSequence);
        if (seq != null && !seq.isEmpty()) {
          Attributes cmd = Optional.ofNullable(p.getAttributes()).orElseGet(Attributes::new);
          cmd.setInt(Tag.Status, VR.US, Status.OneOrMoreFailures);
          cmd.setInt(Tag.NumberOfCompletedSuboperations, VR.US, nbFile);
          cmd.setInt(Tag.NumberOfFailedSuboperations, VR.US, seq.size());
          cmd.setInt(Tag.NumberOfWarningSuboperations, VR.US, 0);
          cmd.setInt(Tag.NumberOfRemainingSuboperations, VR.US, 0);
          p.setAttributes(cmd);
          message =
              seq.stream()
                  .map(
                      s ->
                          s.getString(Tag.ReferencedSOPInstanceUID, "Unknown SopUID") // NON-NLS
                              + " -> "
                              + s.getString(Tag.FailureReason))
                  .collect(Collectors.joining(", "));
          LOGGER.error("STOW-RS error: {}", message);
          return DicomState.buildMessage(
              state, null, new RuntimeException("Failed instances: " + message));
        }
      }
      LOGGER.error("STOW-RS error: {}", message);
    }
    return DicomState.buildMessage(state, message, null);
  }

  private Attributes getResponseOutput(HttpStream httpPost)
      throws IOException, ParserConfigurationException, SAXException {
    int code = httpPost.getResponseCode();
    if (code == HttpURLConnection.HTTP_OK) {
      LOGGER.info(
          "STOW-RS server response message: HTTP Status-Code 200: OK for all the image set");
    } else if (code == HttpURLConnection.HTTP_ACCEPTED || code == HttpURLConnection.HTTP_CONFLICT) {
      LOGGER.warn(
          "STOW-RS server response message: HTTP Status-Code {}: {}",
          code,
          httpPost.getResponseMessage());
      // See
      // http://dicom.nema.org/medical/dicom/current/output/chtml/part18/sect_6.6.html#table_6.6.1-1
      return SAXReader.parse(httpPost.getInputStream());
    } else {
      throw new HttpServerErrorException(
          String.format(
              "STOW-RS server response message: HTTP Status-Code %d: %s",
              code, httpPost.getResponseMessage()));
    }
    return null;
  }
}
