/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.algo;

import java.awt.Rectangle;
import java.util.List;

/**
 * The Class Contour.
 *
 * @author Nicolas Roduit
 */
public class Contour {
  public static final int[] DIRX = {1, 1, 0, -1, -1, -1, 0, 1};
  public static final int[] DIRY = {0, -1, -1, -1, 0, 1, 1, 1};

  private final int coordx;
  private final int coordy;
  private final byte[] codeFreeman;
  private final int area;
  private final List<Double> parameters;

  public Contour(int coordx, int coordy, byte[] codeFreeman, int area, List<Double> parameters) {
    this.coordx = coordx;
    this.coordy = coordy;
    this.codeFreeman = codeFreeman;
    this.area = area;
    this.parameters = parameters;
  }

  public byte[] getCodeFreeman() {
    return codeFreeman;
  }

  public int getCoordx() {
    return coordx;
  }

  public int getCoordy() {
    return coordy;
  }

  public int getArea() {
    return area;
  }

  public List<Double> getParameters() {
    return parameters;
  }

  public Rectangle getBounds() {
    if (codeFreeman == null) {
      return null;
    }
    int[] dirX = DIRX;
    int[] dirY = DIRY;
    int boundsMinX = Integer.MAX_VALUE;
    int boundsMinY = Integer.MAX_VALUE;
    int boundsMaxX = Integer.MIN_VALUE;
    int boundsMaxY = Integer.MIN_VALUE;
    int x = coordx;
    int y = coordy;
    for (int index : codeFreeman) {
      x += dirX[index];
      boundsMinX = Math.min(boundsMinX, x);
      boundsMaxX = Math.max(boundsMaxX, x);
      y += dirY[index];
      boundsMinY = Math.min(boundsMinY, y);
      boundsMaxY = Math.max(boundsMaxY, y);
    }
    return new Rectangle(
        boundsMinX, boundsMinY, boundsMaxX - boundsMinX + 1, boundsMaxY - boundsMinY + 1);
  }

  private static int changeDir(int lastDir, int index) {
    int change = index - lastDir;
    // resolution du passage du 7 au 0
    if (change < -4) {
      change = (change + 8) % 8;
    } else if (change > 4) {
      change = (change - 8) % 8;
    }
    return change;
  }

  private void writeTurnPosition(
      byte[][] matrix, int missX, int missY, int missPos, int offset, byte[] border) {
    int[] dirX = DIRX;
    int[] dirY = DIRY;
    if (offset > 2) {
      // si le pixel de la première couche ne peut pas être écrit, on abandonne pour les autres
      // couches
      int extY = 2;
      lineY:
      while (extY < offset + 1) {
        int extX = 2;
        boolean canWriteX = true;
        while (extX < offset - (extY - 2)) {
          int x = missX + dirX[missPos] * (extX - 2);
          int y = missY + dirY[missPos] * (extY - 2);
          if (matrix[y][x] != border[extX + extY]) {
            if (extX == 2) {
              break lineY;
            } else {
              canWriteX = false;
              break;
            }
          }
          extX++;
        }
        if (canWriteX) {
          int x = missX + dirX[missPos] * (extX - 2);
          int y = missY + dirY[missPos] * (extY - 2);
          if (matrix[y][x] == 0) {
            matrix[y][x] = border[offset + 2];
          }
        }
        extY++;
      }
    } else if (offset == 2) {
      if (matrix[missY][missX] == 0) {
        matrix[missY][missX] = border[4];
      }
    }
  }

  private static void writeNewPosition(
      byte[][] matrix,
      int index,
      int nextDir,
      int newPos1,
      int offset,
      int xdir,
      int ydir,
      byte[] border) {
    int[] dirX = DIRX;
    int[] dirY = DIRY;
    // si un pixel avant la couche à écrire ne peut pas être écrit, on abandonne l'écriture
    int ext = 1;
    boolean canWrite = true;
    while (ext < offset) {
      int x = xdir + dirX[newPos1] * ext;
      int y = ydir + dirY[newPos1] * ext;
      if (matrix[y][x] != border[ext + 2]) {
        canWrite = false;
        break;
      }
      ext++;
    }
    if (canWrite) {
      int x = xdir + dirX[newPos1] * offset;
      int y = ydir + dirY[newPos1] * offset;
      if (matrix[y][x] == 0 && canWriteInDir(index, nextDir, newPos1)) {
        matrix[y][x] = border[offset + 2];
      }
    }
  }

  private static void writeNewPositionWithNoRestriction(
      byte[][] matrix, int newPos1, int offset, int xdir, int ydir, byte[] border) {
    int[] dirX = DIRX;
    int[] dirY = DIRY;
    // si un pixel avant la couche à écrire ne peut pas être écrit, on abandonne l'écriture
    int ext = 1;
    boolean canWrite = true;
    while (ext < offset) {
      int x = xdir + dirX[newPos1] * ext;
      int y = ydir + dirY[newPos1] * ext;
      if (matrix[y][x] != border[ext + 2]) {
        canWrite = false;
        break;
      }
      ext++;
    }
    if (canWrite) {
      int x = xdir + dirX[newPos1] * offset;
      int y = ydir + dirY[newPos1] * offset;
      if (matrix[y][x] == 0) {
        matrix[y][x] = border[offset + 2];
      }
    }
  }

  private static boolean canWriteInDir(int index, int nextDir, int newPos) {
    return (newPos - (index - 4) + 8) % 8 < (nextDir - (index - 4) + 8) % 8;
  }

  private void writeLayer(byte[][] matrix, int xdir, int ydir, byte[] border, int layer) {
    int[] dirX = DIRX;
    int[] dirY = DIRY;
    // 2 le bias pour compter les position des couches de l'intérieur : 1 vers l'extérieur : n
    int offset = layer - 2;
    matrix[ydir - offset][xdir] = border[layer];
    // matrix[ydir - offset+1][xdir - offset] = border[layer];
    matrix[ydir][xdir] = border[1];
    int lastDir = codeFreeman[codeFreeman.length - 1]; // dernière direction
    for (int i = 0; i < codeFreeman.length; i++) {
      int index = codeFreeman[i];
      int nextDir;
      if (i == codeFreeman.length - 1) {
        nextDir = codeFreeman[0];
      } else {
        nextDir = codeFreeman[i + 1];
      }
      xdir += dirX[index];
      ydir += dirY[index];
      matrix[ydir][xdir] = border[1];
      int change = changeDir(lastDir, index);
      if (change == -2) {
        int newPos1 = (index - 1 + 8) % 8;
        writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
      } else if (change == -1 || change == 0) {
        if (index % 2 == 0) {
          int newPos1 = (index - 2 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
        } else {
          int newPos1 = (index - 1 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
          int newPos2 = (index - 3 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos2, offset, xdir, ydir, border);
        }
      } else if (change == 1) {
        if (index % 2 == 0) {
          int newPos1 = (index - 2 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
        } else {
          int newPos1 = (index - 1 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
          int newPos2 = (index - 3 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos2, offset, xdir, ydir, border);
          if (changeDir(index, nextDir) >= 0) {
            // position pour allonger la courbure des couches extérieures
            int newPos3 = (index - 2 + 8) % 8;
            int missX = xdir + dirX[newPos3];
            int missY = ydir + dirY[newPos3];
            writeTurnPosition(matrix, missX, missY, newPos3, offset, border);
          }
        }
      } else if (change == 2) {
        if (index % 2 == 0) {
          int newPos1 = (index - 2 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
          // revient en arrière (direction opposée)
          int newPos2 = (index - 4 + 8) % 8;
          int missX = xdir + dirX[newPos2];
          int missY = ydir + dirY[newPos2];
          writeNewPosition(matrix, index, nextDir, newPos1, offset, missX, missY, border);
          // position pour allonger la courbure des couches extérieures
          int newPos3 = (lastDir - 1 + 8) % 8;
          int missX2 = missX + dirX[newPos3];
          int missY2 = missY + dirY[newPos3];
          writeTurnPosition(matrix, missX2, missY2, newPos3, offset, border);
        } else {
          int newPos1 = (index - 1 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
          int newPos2 = (index - 3 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos2, offset, xdir, ydir, border);
          // position pour allonger la courbure des couches extérieures
          int newPos3 = (index - 2 + 8) % 8;
          int missX = xdir + dirX[newPos3];
          int missY = ydir + dirY[newPos3];
          writeTurnPosition(matrix, missX, missY, newPos3, offset, border);
        }
      } else if (change == 3 || change == 4 || change == -4) {
        if (index % 2 == 0) {
          int newPos1 = (index - 2 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
          // revient en arrière (direction opposée)
          int newPos2 = (index - 4 + 8) % 8;
          int missX = xdir + dirX[newPos2];
          int missY = ydir + dirY[newPos2];
          writeNewPositionWithNoRestriction(matrix, newPos1, offset, missX, missY, border);
          if (change == 3) {
            // position pour allonger la courbure des couches extérieures
            int missX2 = missX + dirX[lastDir];
            int missY2 = missY + dirY[lastDir];
            writeTurnPosition(matrix, missX2, missY2, lastDir, offset, border);
          } else { // change 4 ou -4
            writeNewPositionWithNoRestriction(matrix, newPos2, offset, missX, missY, border);
            // postion précédent n'arrive pas à écrire à cause de la restriction (qui ne marche pas
            // parce
            // que
            // le calcul dépassse la moitié d'un cadran
            int newPos6 = (newPos2 - 2 + 8) % 8;
            writeNewPositionWithNoRestriction(matrix, newPos6, offset, missX, missY, border);
            // position pour allonger la courbure des couches extérieures
            int newPos4 = (newPos2 - 1 + 8) % 8;
            int missX2 = missX + dirX[newPos4];
            int missY2 = missY + dirY[newPos4];
            writeTurnPosition(matrix, missX2, missY2, newPos4, offset, border);
            int newPos5 = (newPos2 + 1 + 8) % 8;
            missX2 = missX + dirX[newPos5];
            missY2 = missY + dirY[newPos5];
            writeTurnPosition(matrix, missX2, missY2, newPos5, offset, border);
          }
        } else { // change 3 et 4 identique
          int newPos1 = (index - 1 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos1, offset, xdir, ydir, border);
          // cette position est forcément plus au sommet de l'angle
          int newPos2 = (index - 3 + 8) % 8;
          writeNewPosition(matrix, index, nextDir, newPos2, offset, xdir, ydir, border);
          // 1 er position pour allonger la courbure des couches extérieures
          int newPos3 = (index - 2 + 8) % 8;
          int missX = xdir + dirX[newPos3];
          int missY = ydir + dirY[newPos3];
          writeTurnPosition(matrix, missX, missY, newPos3, offset, border);
          // revient en arrière (direction opposée)
          int newPos4 = (index - 4 + 8) % 8;
          int missX2 = xdir + dirX[newPos4];
          int missY2 = ydir + dirY[newPos4];
          if (change == 4 || change == -4) {
            // postion précédent n'arrive pas à écrire à cause de la restriction (qui ne marche pas
            // parce
            // que
            // le calcul dépassse la moitié d'un cadran
            int newPos6 = (newPos4 - 1 + 8) % 8;
            writeNewPositionWithNoRestriction(matrix, newPos6, offset, missX2, missY2, border);
            newPos6 = (newPos4 - 3 + 8) % 8;
            writeNewPositionWithNoRestriction(matrix, newPos6, offset, missX2, missY2, border);
          }
          int newPos5 = (newPos4 + 1 + 8) % 8;
          writeNewPositionWithNoRestriction(matrix, newPos5, offset, missX2, missY2, border);
          int missX3 = missX2 + dirX[newPos4];
          int missY3 = missY2 + dirY[newPos4];
          writeTurnPosition(matrix, missX3, missY3, newPos4, offset, border);
          if (change == 4 || change == -4) {
            int newPos7 = (newPos4 - 2 + 8) % 8;
            missX3 = missX2 + dirX[newPos7];
            missY3 = missY2 + dirY[newPos7];
            writeTurnPosition(matrix, missX3, missY3, newPos7, offset, border);
          }
        }
      }
      lastDir = index;
    }
  }

  public byte[][] dilate(int layer) {
    /* 3 2 1 */
    /* 4 0 */
    /* 5 6 7 */
    /* Table given index offset for each of the 8 directions. */
    if (layer < 1) {
      return null;
    }
    Rectangle bound = getBounds();
    // +2 pour la dilation de la forme d'un pixel
    byte[][] matrix = new byte[bound.height + 2 * layer][bound.width + 2 * layer];
    byte[] border = new byte[layer + 3];
    for (int i = 0; i < border.length; i++) {
      border[i] = (byte) i;
    }
    int xdir = coordx - bound.x + layer;
    int ydir = layer;
    for (int k = 3; k < layer + 3; k++) {
      writeLayer(matrix, xdir, ydir, border, k);
    }
    return matrix;
  }

  public static Contour getContourFromChainListCoord(List<ChainPoint> chain) {
    byte[] val = new byte[chain.size()];
    ChainPoint last = chain.get(0);
    for (int j = 1; j < chain.size(); j++) {
      ChainPoint pt = chain.get(j);
      val[j - 1] = getDirFromTwoCoord(last, pt);
      last = pt;
    }
    ChainPoint first = chain.get(0);
    val[val.length - 1] = getDirFromTwoCoord(chain.get(chain.size() - 1), first);
    return new Contour(first.x, first.y, val, 50, null);
  }

  public static byte getDirFromTwoCoord(ChainPoint last, ChainPoint next) {
    int x = next.x - last.x;
    int y = next.y - last.y;
    if (x == 1) {
      if (y == 1) {
        return (byte) 7;
      } else if (y == -1) {
        return (byte) 1;
      } else {
        return (byte) 0;
      }
    } else if (x == -1) {
      if (y == 1) {
        return (byte) 5;
      } else if (y == -1) {
        return (byte) 3;
      } else {
        return (byte) 4;
      }
    } else {
      if (y == 1) {
        return (byte) 6;
      } else {
        return (byte) 2;
      }
    }
  }
}
