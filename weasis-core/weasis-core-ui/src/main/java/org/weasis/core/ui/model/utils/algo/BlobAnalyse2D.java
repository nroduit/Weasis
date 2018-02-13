/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.utils.algo;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.RandomIterFactory;

import org.weasis.core.ui.model.utils.GraphicUtil;

/**
 * The Class BlobAnalyse2D.
 *
 * @author Nicolas Roduit
 */
public class BlobAnalyse2D {

    private PlanarImage source;
    // méthode d'itération qui permet de connaître la postion des pixels
    private RandomIter src;
    Rectangle imgbound;
    private final Point firstPoint;
    private boolean[][] visited;
    private final int dwidth;
    private final int dheight;
    private ArrayList<Point> blob = null;
    private Shape shape;
    private int area;

    public BlobAnalyse2D(Shape shape) {
        this.shape = shape;
        this.area = 0;
        this.source = GraphicUtil.getGraphicAsImage(shape);
        this.imgbound = source.getBounds();
        this.src = RandomIterFactory.create(source, null);
        this.dwidth = source.getWidth();
        this.dheight = source.getHeight();
        this.firstPoint = new Point();
        iniToVisited(src, 0);
    }

    public BlobAnalyse2D(PlanarImage binary) {
        this.area = 0;
        this.source = binary;
        this.imgbound = binary.getBounds();
        this.src = RandomIterFactory.create(binary, null);
        this.dwidth = binary.getWidth();
        this.dheight = binary.getHeight();
        this.firstPoint = new Point();
    }

    private void iniToVisited(RandomIter data, int val) {
        int[] pix = { 0 };
        visited = new boolean[dheight][dwidth];
        // met à true les pixels du background
        for (int j = 0; j < dheight; j++) {
            for (int i = 0; i < dwidth; i++) {
                data.getPixel(i + imgbound.x, j + imgbound.y, pix);
                if (pix[0] == val) {
                    visited[j][i] = true;
                }
            }
        }
    }

    // public ClassGraphic transformToClassGraphic(ClassData classData) {
    // for (int j = 0; j < dheight; j++) {
    // for (int i = 0; i < dwidth; i++) {
    // if (visited[j][i] == false) {
    // int area = getArea();
    // Contour contour = new Contour(shape.getBounds().x + i, shape.getBounds().y + j, chain8(i, j), area,
    // getStatValue(blobSumCoorXY));
    // ClassGraphic object = new ClassGraphic(classData, contour);
    // return object;
    // }
    // }
    // }
    // return null;
    // }

    public Contour getContour() {
        for (int j = 0; j < dheight; j++) {
            for (int i = 0; i < dwidth; i++) {
                if (visited[j][i] == false) {
                    int area = getArea();
                    Contour contour = new Contour(shape.getBounds().x + i, shape.getBounds().y + j, chain8(i, j), area,
                        getStatValue(blob));
                    return contour;
                }
            }
        }
        return null;
    }

    public Vector<Contour> getHolesContour() {
        Vector<Contour> holes = new Vector<>();
        iniToVisited(src, 0);
        int[] area = { 0 };
        for (int m = 0; m < dheight; m++) {
            for (int n = 0; n < dwidth; n++) {
                if (!visited[m][n]) {
                    if (!growingBHoleSize(n, m, area)) {
                        byte[] holeChain = chainHole8(n, m - 1);
                        Contour contour = new Contour(shape.getBounds().x + +n, shape.getBounds().y + +m - 1, holeChain,
                            area[0], null);
                        holes.add(contour);
                    }
                }
            }
        }
        holes.trimToSize();
        holes = holes.isEmpty() ? null : holes;
        return holes;
    }

    private byte[] chainHole8(int x, int y) {
        /* Table given index offset for each of the 8 directions. */
        int[] dx = { 1, 1, 0, -1, -1, -1, 0, 1 };
        int[] dy = { 0, -1, -1, -1, 0, 1, 1, 1 };
        Byte direction = null;
        boolean foundPix;
        byte dirTemp;
        int row = y;
        int col = x;
        int lastdir = 6; // dernière direction
        List<Byte> chain = new ArrayList<>();
        int[] pix = { 0 };
        src.getPixel(x, y, pix);
        // initalise val avec la valeur d'intensité du blob
        int val = pix[0];
        do {
            foundPix = false;
            for (int i = lastdir + 1; i < lastdir + 8; i++) { /* Look for next */
                dirTemp = (byte) (i % 8); // reste de la division par 8
                int xp4 = dx[dirTemp] + col;
                int yp4 = dy[dirTemp] + row;
                // teste si la nouvelle position est bien dans l'image
                if (xp4 >= 0 && xp4 < dwidth && yp4 >= 0 && yp4 < dheight) {
                    src.getPixel(xp4, yp4, pix);
                    if (pix[0] == val) { // si la nouvelle position à un valeur d'intensité identique
                        direction = dirTemp;
                        foundPix = true;
                        break;
                    }
                }
            }
            if (foundPix) { /* Found a next pixel ... */
                chain.add(direction); /* Save direction as code */
                row += dy[direction.intValue()];
                col += dx[direction.intValue()];
                lastdir = (direction + 5) % 8;
            } else {
                break;
            }
        } while ((row != y) || (col != x)); /* Stop when next to start pixel */
        byte[] tab2;
        tab2 = new byte[chain.size()];
        for (int i = 0; i < tab2.length; i++) {
            tab2[i] = chain.get(i);
        }
        return tab2;
    }

    private boolean growingBHoleSize(int i, int j, int[] area) {
        Stack<Integer[]> stack = new Stack<>();
        area[0] = 1;
        boolean borderHit = false;
        // teste si le point de départ touche le bord
        if (i == 0 || i == dwidth - 1 || j == 0 || j == dheight - 1) {
            borderHit = true;
        }
        visited[j][i] = true;
        stack.push(new Integer[] { i, j });
        while (!stack.empty()) {
            Integer ai[] = stack.pop();
            int x = ai[0];
            int y = ai[1];
            int xp1 = x;
            int yp1 = y - 1;
            if (xp1 >= 0 && xp1 < dwidth && yp1 >= 0 && yp1 < dheight) {
                if (visited[yp1][xp1] == false) {
                    if (xp1 == 0 || xp1 == dwidth - 1 || yp1 == 0 || yp1 == dheight - 1) {
                        borderHit = true;
                    } else {
                        area[0]++;
                    }
                    visited[yp1][xp1] = true;
                    stack.push(new Integer[] { xp1, yp1 });
                }
            }
            int xp2 = x;
            int yp2 = y + 1;
            if (xp2 >= 0 && xp2 < dwidth && yp2 >= 0 && yp2 < dheight) {
                if (visited[yp2][xp2] == false) {
                    if (xp2 == 0 || xp2 == dwidth - 1 || yp2 == 0 || yp2 == dheight - 1) {
                        borderHit = true;
                    } else {
                        area[0]++;
                    }
                    visited[yp2][xp2] = true;
                    stack.push(new Integer[] { xp2, yp2 });
                }
            }
            int xp3 = x + 1;
            int yp3 = y;
            if (xp3 >= 0 && xp3 < dwidth && yp3 >= 0 && yp3 < dheight) {
                if (visited[yp3][xp3] == false) {
                    if (xp3 == 0 || xp3 == dwidth - 1 || yp3 == 0 || yp3 == dheight - 1) {
                        borderHit = true;
                    } else {
                        area[0]++;
                    }
                    visited[yp3][xp3] = true;
                    stack.push(new Integer[] { xp3, yp3 });
                }
            }
            int xp4 = x - 1;
            int yp4 = y;
            if (xp4 >= 0 && xp4 < dwidth && yp4 >= 0 && yp4 < dheight) {
                if (visited[yp4][xp4] == false) {
                    if (xp4 == 0 || xp4 == dwidth - 1 || yp4 == 0 || yp4 == dheight - 1) {
                        borderHit = true;
                    } else {
                        area[0]++;
                    }
                    visited[yp4][xp4] = true;
                    stack.push(new Integer[] { xp4, yp4 });
                }
            }
        }
        return borderHit;
    }

    public int getArea() {
        // renvoie l'aire de l'image de ROIShape
        if (blob == null) {
            for (int j = 0; j < dheight; j++) {
                for (int i = 0; i < dwidth; i++) {
                    if (!visited[j][i]) {
                        firstPoint.x = i;
                        firstPoint.y = j;
                        return area = growingSize(i, j);
                    }
                }
            }
        }
        return area;
    }

    public double getPerimeter() {
        getArea();
        return computePerimeter(chain8(firstPoint.x, firstPoint.y));
    }

    public static double computePerimeter(byte[] chain) {
        int corner = 0;
        int evenCode = 0;
        int oddCode = 0;
        // si le blob mesure 1 pixel, il y a que le point de départ et la chaine est nulle
        if (chain.length == 0) {
            return 2d;
        }
        // process le 1er élément de la chaine
        if ((chain[0] % 2) != 0) {
            oddCode++;
        } else {
            evenCode++;
        }
        for (int i = 1; i < chain.length; i++) {
            int code = chain[i];
            // si il y a un reste à la division par 2, alors incrémente le compteur impair, sinon pair
            if (code % 2 != 0) {
                oddCode++;
            } else {
                evenCode++;
                // le compteur corner comptabilise le nombre de changement de direction de la chaîne
            }
            if (code != chain[i - 1]) {
                corner++;
            }
        }
        // Vossepoel & Smeulders (1982) :
        // Ne = Number of even chain codes
        // No = Number of odd chain codes
        // Nc = Number of "corners" (where the chain code changes)
        // Perimeter = (0.980) Ne + (1.406) No - (0.091) Nc
        return (0.98d * evenCode) + (1.406d * oddCode) - (0.091d * corner);
    }

    public List<Point> getBlobSumCoorXY() {
        getArea();
        return blob;
    }

    private byte[] chain8(int x, int y) {
        x += imgbound.x;
        y += imgbound.y;
        // Table given index offset for each of the 8 directions.
        int[] dx = { 1, 1, 0, -1, -1, -1, 0, 1 };
        int[] dy = { 0, -1, -1, -1, 0, 1, 1, 1 };
        Byte direction = null;
        boolean foundPix;
        int dirTemp;
        int row = y;
        int col = x;
        int lastdir = 4; // dernière direction
        List<Byte> chain = new ArrayList<>();
        // intialise les valeurs de la boundingBox
        int maxX = imgbound.x + imgbound.width;
        int maxY = imgbound.y + imgbound.height;
        int[] pix = { 0 };
        src.getPixel(x, y, pix);
        // initalise val avec la valeur d'intensité du blob
        int val = pix[0];
        do {
            foundPix = false;
            for (int i = lastdir + 1; i < lastdir + 8; i++) { /* Look for next */
                dirTemp = i % 8; // reste de la division par 8
                int xp4 = dx[dirTemp] + col;
                int yp4 = dy[dirTemp] + row;
                // teste si la nouvelle position est bien dans l'image
                if (xp4 >= imgbound.x && xp4 < maxX && yp4 >= imgbound.y && yp4 < maxY) {
                    src.getPixel(xp4, yp4, pix);
                    if (pix[0] == val) { // si la nouvelle position à un valeur d'intensité identique
                        // attribution d'un code de direction de Freeman en connectivité 8
                        // code de Freeman : où 0 est à l'ouest, 1 au nord-ouest, 2 au nord, 3 au nord-est 4 à l'est ...
                        direction = (byte) dirTemp;
                        foundPix = true;
                        break;
                    }
                }
            }
            if (foundPix) { /* Found a next pixel ... */
                chain.add(direction); /* Save direction as code */
                row += dy[direction.intValue()];
                col += dx[direction.intValue()];
                lastdir = (direction + 5) % 8;
            } else {
                break; /* NO next pixel, la chaine n'est pas fermée */
            }
        } while ((row != y) || (col != x)); /* Stop when next to start pixel */
        byte[] tab = new byte[chain.size()];
        for (int i = 0; i < tab.length; i++) {
            tab[i] = chain.get(i);
        }
        return tab;
    }

    int growingSize(int i, int j) {
        blob = new ArrayList<>();
        Stack<Point> stack = new Stack<>();
        int size = 1;
        Point startp = new Point(i, j);
        blob.add(startp);
        visited[j][i] = true;
        stack.push(startp);
        while (!stack.empty()) {
            Point lp = stack.pop();
            int xp1 = lp.x;
            int yp1 = lp.y - 1;
            if (xp1 >= 0 && xp1 < dwidth && yp1 >= 0 && yp1 < dheight) {
                if (visited[yp1][xp1] == false) {
                    Point p = new Point(xp1, yp1);
                    blob.add(p);
                    size++;
                    visited[yp1][xp1] = true;
                    stack.push(p);
                }
            }
            int xp2 = lp.x;
            int yp2 = lp.y + 1;
            if (xp2 >= 0 && xp2 < dwidth && yp2 >= 0 && yp2 < dheight) {
                if (visited[yp2][xp2] == false) {
                    Point p = new Point(xp2, yp2);
                    blob.add(p);
                    size++;
                    visited[yp2][xp2] = true;
                    stack.push(p);
                }
            }
            int xp3 = lp.x + 1;
            int yp3 = lp.y;
            if (xp3 >= 0 && xp3 < dwidth && yp3 >= 0 && yp3 < dheight) {
                if (visited[yp3][xp3] == false) {
                    Point p = new Point(xp3, yp3);
                    blob.add(p);
                    size++;
                    visited[yp3][xp3] = true;
                    stack.push(p);
                }
            }
            int xp4 = lp.x - 1;
            int yp4 = lp.y;
            if (xp4 >= 0 && xp4 < dwidth && yp4 >= 0 && yp4 < dheight) {
                if (visited[yp4][xp4] == false) {
                    Point p = new Point(xp4, yp4);
                    blob.add(p);
                    size++;
                    visited[yp4][xp4] = true;
                    stack.push(p);
                }
            }
            int xp5 = lp.x + 1;
            int yp5 = lp.y - 1;
            if (xp5 >= 0 && xp5 < dwidth && yp5 >= 0 && yp5 < dheight) {
                if (visited[yp5][xp5] == false) {
                    Point p = new Point(xp5, yp5);
                    blob.add(p);
                    size++;
                    visited[yp5][xp5] = true;
                    stack.push(p);
                }
            }
            int xp6 = lp.x - 1;
            int yp6 = lp.y + 1;
            if (xp6 >= 0 && xp6 < dwidth && yp6 >= 0 && yp6 < dheight) {
                if (visited[yp6][xp6] == false) {
                    Point p = new Point(xp6, yp6);
                    blob.add(p);
                    size++;
                    visited[yp6][xp6] = true;
                    stack.push(p);
                }
            }
            int xp7 = lp.x + 1;
            int yp7 = lp.y + 1;
            if (xp7 >= 0 && xp7 < dwidth && yp7 >= 0 && yp7 < dheight) {
                if (visited[yp7][xp7] == false) {
                    Point p = new Point(xp7, yp7);
                    blob.add(p);
                    size++;
                    visited[yp7][xp7] = true;
                    stack.push(p);
                }
            }
            int xp8 = lp.x - 1;
            int yp8 = lp.y - 1;
            if (xp8 >= 0 && xp8 < dwidth && yp8 >= 0 && yp8 < dheight) {
                if (visited[yp8][xp8] == false) {
                    Point p = new Point(xp8, yp8);
                    blob.add(p);
                    size++;
                    visited[yp8][xp8] = true;
                    stack.push(p);
                }
            }
        }
        return size;
    }

    public static List<Double> getStatValue(List<Point> blobXY) {
        ArrayList<Double> list = new ArrayList<>(11);
        int sumX = 0;
        int sumY = 0;
        for (Point p : blobXY) {
            sumX += p.x;
            sumY += p.y;
        }
        double mx = (double) sumX / (double) blobXY.size();
        double my = (double) sumY / (double) blobXY.size();
        double u20 = 0.0; // moment central bivarié de degré 2 pour x et 0 pour y
        double u02 = 0.0; // moment central bivarié de degré 0 pour x et 2 pour y
        double u11 = 0.0; // moment central bivarié de degré 1 pour x et 1 pour y
        double u21 = 0.0; // moment central bivarié de degré 2 pour x et 1 pour y
        double u12 = 0.0; // moment central bivarié de degré 1 pour x et 2 pour y
        double u30 = 0.0; // moment central bivarié de degré 3 pour x et 0 pour y
        double u03 = 0.0; // moment central bivarié de degré 0 pour x et 3 pour y
        for (Point p : blobXY) {
            // central bivariate moments
            double x = p.getX();
            double y = p.getY();
            u11 += (x - mx) * (y - my);
            u20 += (x - mx) * (x - mx);
            u02 += (y - my) * (y - my);
            u21 += (x - mx) * (x - mx) * (y - my);
            u12 += (x - mx) * (y - my) * (y - my);
            u30 += (x - mx) * (x - mx) * (x - mx);
            u03 += (y - my) * (y - my) * (y - my);
        }
        list.add(mx);
        list.add(my);
        list.add(u11);
        list.add(u20);
        list.add(u02);
        list.add(u21);
        list.add(u12);
        list.add(u30);
        list.add(u03);
        return list;
    }
}
