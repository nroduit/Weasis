/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.util;

import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.ServiceUIFactory;
import javax.print.attribute.Attribute;
import javax.print.attribute.AttributeSet;
import javax.print.attribute.PrintServiceAttribute;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.event.PrintServiceAttributeListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.service.AuditLog;

/**
 * ForcedAcceptPrintService is a PrintService wrapper class that allows printing to be attempted even if Java thinks the
 * 'Printer is not accepting job'. It is recommended to use this after prompting the user, so they will see that the
 * printer is reporting as offline and can choose to "force it" to try printing anyway.
 * <p/>
 * This hack gets around annoying 'Printer is not accepting job' errors in Java 5/6 that don't occur in Java 1.4. This
 * was enough of a problem for our 1000+ users that it was the sole reason we could not move the product up from Java
 * 1.4. Hence, the hack was invented as we had the problem with users whose printers were clearly online and they could
 * print from any non-Java application.
 * <p/>
 * Turns out this hack is also useful for printing to the latest inkjet printers that have chips in the cartridges and
 * stay in a 'No ink' status once empty. This is presumably to cause an inconvenience for cartridge refillers, but we're
 * the ones who get the support calls from users that printing is not working, so it's an inconvenience to everyone
 * except the printer manufacturer.
 * <p/>
 */
public class ForcedAcceptPrintService implements PrintService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForcedAcceptPrintService.class);

    private final PrinterJob thePrintJob;
    private final PrintService delegate;

    /**
     * Private constructor as this only works as a one-shot per print attempt. Use the static method above to hack a
     * PrintJob, then tell it to print. The hack is gone by the time printing occurs and this instance will be garbage
     * collected due to having no other references once the PrintJob is back to its original state.
     *
     * @param printJob
     *            the print job to affect
     */
    private ForcedAcceptPrintService(PrinterJob printJob) {
        this.thePrintJob = printJob;
        this.delegate = printJob.getPrintService();

        try {
            thePrintJob.setPrintService(this);
            // replace the private PrintService field on the PrintJob instance with a reference
            // to our replacement PrintService so that we can intercept calls to getAttributes().
            // it is expected that the first thing the PrintJob will do is check it's PrintService's
            // PrinterIsAcceptingJobs attribute, at which point we'll force it to think it is accepting
            // jobs and restore the PrintService to the original instance to get out of the way.
            // The only real requirement is that the PrintJob does not cast the PrintService
            // to it's expected type until after it has checked the PrinterIsAcceptingJobs
            // attribute.

        } catch (PrinterException e) {
            AuditLog.logError(LOGGER, e, "Set Print Service"); //$NON-NLS-1$
        }

    }

    /**
     * Tweak the PrintJob to think this class is it's PrintService, long enough to override the PrinterIsAcceptingJobs
     * attribute. If it doesn't work out or the printer really is offline then it's no worse than if this hack was not
     * used.
     *
     * @param printJob
     *            the print job to affect
     */
    public static void setupPrintJob(PrinterJob printJob) {
        new ForcedAcceptPrintService(printJob);
    }

    /** Restore the PrintJob's PrintService to what it was originally. */
    private void restoreServiceReference() {

        try {
            thePrintJob.setPrintService(delegate);
        } catch (PrinterException e) {
            AuditLog.logError(LOGGER, e, "Restore Print Service"); //$NON-NLS-1$
        }

    }

    /**
     *
     * getAttribute is the one PrintService method we want to intercept to override the
     *
     * PrinterIsAcceptingJobs attribute.
     */
    @Override
    public <T extends PrintServiceAttribute> T getAttribute(Class<T> category) {

        if (category.equals(PrinterIsAcceptingJobs.class)) {
            // once we've overridden the return value for the PrinterIsAcceptingJobs attribute we're done.
            // put the PrintJob's PrintService back to what it was.
            restoreServiceReference();
            return (T) PrinterIsAcceptingJobs.ACCEPTING_JOBS;
        }

        return delegate.getAttribute(category);

    }

    @Override
    public DocPrintJob createPrintJob() {
        return delegate.createPrintJob();
    }

    @Override
    public void addPrintServiceAttributeListener(PrintServiceAttributeListener listener) {
        delegate.addPrintServiceAttributeListener(listener);
    }

    @Override
    public PrintServiceAttributeSet getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Object getDefaultAttributeValue(Class<? extends Attribute> category) {
        return delegate.getDefaultAttributeValue(category);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public ServiceUIFactory getServiceUIFactory() {
        return delegate.getServiceUIFactory();
    }

    @Override
    public Class<?>[] getSupportedAttributeCategories() {
        return delegate.getSupportedAttributeCategories();
    }

    @Override
    public Object getSupportedAttributeValues(Class<? extends Attribute> category, DocFlavor flavor,
        AttributeSet attributes) {
        return delegate.getSupportedAttributeValues(category, flavor, attributes);
    }

    @Override
    public DocFlavor[] getSupportedDocFlavors() {
        return delegate.getSupportedDocFlavors();
    }

    @Override
    public AttributeSet getUnsupportedAttributes(DocFlavor flavor, AttributeSet attributes) {
        return delegate.getUnsupportedAttributes(flavor, attributes);
    }

    @Override
    public boolean isAttributeCategorySupported(Class<? extends Attribute> category) {
        return delegate.isAttributeCategorySupported(category);
    }

    @Override
    public boolean isAttributeValueSupported(Attribute attrval, DocFlavor flavor, AttributeSet attributes) {
        return delegate.isAttributeValueSupported(attrval, flavor, attributes);
    }

    @Override
    public boolean isDocFlavorSupported(DocFlavor flavor) {
        return delegate.isDocFlavorSupported(flavor);
    }

    @Override
    public void removePrintServiceAttributeListener(PrintServiceAttributeListener listener) {
        delegate.removePrintServiceAttributeListener(listener);
    }

}