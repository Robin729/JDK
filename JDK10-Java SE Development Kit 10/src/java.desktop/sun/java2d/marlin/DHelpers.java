/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package sun.java2d.marlin;

import static java.lang.Math.PI;
import java.util.Arrays;
import sun.java2d.marlin.stats.Histogram;
import sun.java2d.marlin.stats.StatLong;

final class DHelpers implements MarlinConst {

    private DHelpers() {
        throw new Error("This is a non instantiable class");
    }

    static boolean within(final double x, final double y, final double err) {
        final double d = y - x;
        return (d <= err && d >= -err);
    }

    static int quadraticRoots(final double a, final double b,
                              final double c, double[] zeroes, final int off)
    {
        int ret = off;
        double t;
        if (a != 0.0d) {
            final double dis = b*b - 4*a*c;
            if (dis > 0.0d) {
                final double sqrtDis = Math.sqrt(dis);
                // depending on the sign of b we use a slightly different
                // algorithm than the traditional one to find one of the roots
                // so we can avoid adding numbers of different signs (which
                // might result in loss of precision).
                if (b >= 0.0d) {
                    zeroes[ret++] = (2.0d * c) / (-b - sqrtDis);
                    zeroes[ret++] = (-b - sqrtDis) / (2.0d * a);
                } else {
                    zeroes[ret++] = (-b + sqrtDis) / (2.0d * a);
                    zeroes[ret++] = (2.0d * c) / (-b + sqrtDis);
                }
            } else if (dis == 0.0d) {
                t = (-b) / (2.0d * a);
                zeroes[ret++] = t;
            }
        } else {
            if (b != 0.0d) {
                t = (-c) / b;
                zeroes[ret++] = t;
            }
        }
        return ret - off;
    }

    // find the roots of g(t) = d*t^3 + a*t^2 + b*t + c in [A,B)
    static int cubicRootsInAB(double d, double a, double b, double c,
                              double[] pts, final int off,
                              final double A, final double B)
    {
        if (d == 0.0d) {
            int num = quadraticRoots(a, b, c, pts, off);
            return filterOutNotInAB(pts, off, num, A, B) - off;
        }
        // From Graphics Gems:
        // http://tog.acm.org/resources/GraphicsGems/gems/Roots3And4.c
        // (also from awt.geom.CubicCurve2D. But here we don't need as
        // much accuracy and we don't want to create arrays so we use
        // our own customized version).

        // normal form: x^3 + ax^2 + bx + c = 0
        a /= d;
        b /= d;
        c /= d;

        //  substitute x = y - A/3 to eliminate quadratic term:
        //     x^3 +Px + Q = 0
        //
        // Since we actually need P/3 and Q/2 for all of the
        // calculations that follow, we will calculate
        // p = P/3
        // q = Q/2
        // instead and use those values for simplicity of the code.
        double sq_A = a * a;
        double p = (1.0d/3.0d) * ((-1.0d/3.0d) * sq_A + b);
        double q = (1.0d/2.0d) * ((2.0d/27.0d) * a * sq_A - (1.0d/3.0d) * a * b + c);

        // use Cardano's formula

        double cb_p = p * p * p;
        double D = q * q + cb_p;

        int num;
        if (D < 0.0d) {
            // see: http://en.wikipedia.org/wiki/Cubic_function#Trigonometric_.28and_hyperbolic.29_method
            final double phi = (1.0d/3.0d) * Math.acos(-q / Math.sqrt(-cb_p));
            final double t = 2.0d * Math.sqrt(-p);

            pts[ off+0 ] = ( t * Math.cos(phi));
            pts[ off+1 ] = (-t * Math.cos(phi + (PI / 3.0d)));
            pts[ off+2 ] = (-t * Math.cos(phi - (PI / 3.0d)));
            num = 3;
        } else {
            final double sqrt_D = Math.sqrt(D);
            final double u =   Math.cbrt(sqrt_D - q);
            final double v = - Math.cbrt(sqrt_D + q);

            pts[ off ] = (u + v);
            num = 1;

            if (within(D, 0.0d, 1e-8d)) {
                pts[off+1] = -(pts[off] / 2.0d);
                num = 2;
            }
        }

        final double sub = (1.0d/3.0d) * a;

        for (int i = 0; i < num; ++i) {
            pts[ off+i ] -= sub;
        }

        return filterOutNotInAB(pts, off, num, A, B) - off;
    }

    static double evalCubic(final double a, final double b,
                           final double c, final double d,
                           final double t)
    {
        return t * (t * (t * a + b) + c) + d;
    }

    static double evalQuad(final double a, final double b,
                          final double c, final double t)
    {
        return t * (t * a + b) + c;
    }

    // returns the index 1 past the last valid element remaining after filtering
    static int filterOutNotInAB(double[] nums, final int off, final int len,
                                final double a, final double b)
    {
        int ret = off;
        for (int i = off, end = off + len; i < end; i++) {
            if (nums[i] >= a && nums[i] < b) {
                nums[ret++] = nums[i];
            }
        }
        return ret;
    }

    static double linelen(double x1, double y1, double x2, double y2) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        return Math.sqrt(dx*dx + dy*dy);
    }

    static void subdivide(double[] src, int srcoff, double[] left, int leftoff,
                          double[] right, int rightoff, int type)
    {
        switch(type) {
        case 6:
            DHelpers.subdivideQuad(src, srcoff, left, leftoff, right, rightoff);
            return;
        case 8:
            DHelpers.subdivideCubic(src, srcoff, left, leftoff, right, rightoff);
            return;
        default:
            throw new InternalError("Unsupported curve type");
        }
    }

    static void isort(double[] a, int off, int len) {
        for (int i = off + 1, end = off + len; i < end; i++) {
            double ai = a[i];
            int j = i - 1;
            for (; j >= off && a[j] > ai; j--) {
                a[j+1] = a[j];
            }
            a[j+1] = ai;
        }
    }

    // Most of these are copied from classes in java.awt.geom because we need
    // both single and double precision variants of these functions, and Line2D,
    // CubicCurve2D, QuadCurve2D don't provide them.
    /**
     * Subdivides the cubic curve specified by the coordinates
     * stored in the <code>src</code> array at indices <code>srcoff</code>
     * through (<code>srcoff</code>&nbsp;+&nbsp;7) and stores the
     * resulting two subdivided curves into the two result arrays at the
     * corresponding indices.
     * Either or both of the <code>left</code> and <code>right</code>
     * arrays may be <code>null</code> or a reference to the same array
     * as the <code>src</code> array.
     * Note that the last point in the first subdivided curve is the
     * same as the first point in the second subdivided curve. Thus,
     * it is possible to pass the same array for <code>left</code>
     * and <code>right</code> and to use offsets, such as <code>rightoff</code>
     * equals (<code>leftoff</code> + 6), in order
     * to avoid allocating extra storage for this common point.
     * @param src the array holding the coordinates for the source curve
     * @param srcoff the offset into the array of the beginning of the
     * the 6 source coordinates
     * @param left the array for storing the coordinates for the first
     * half of the subdivided curve
     * @param leftoff the offset into the array of the beginning of the
     * the 6 left coordinates
     * @param right the array for storing the coordinates for the second
     * half of the subdivided curve
     * @param rightoff the offset into the array of the beginning of the
     * the 6 right coordinates
     * @since 1.7
     */
    static void subdivideCubic(double[] src, int srcoff,
                               double[] left, int leftoff,
                               double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx1 = src[srcoff + 2];
        double ctrly1 = src[srcoff + 3];
        double ctrlx2 = src[srcoff + 4];
        double ctrly2 = src[srcoff + 5];
        double x2 = src[srcoff + 6];
        double y2 = src[srcoff + 7];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 6] = x2;
            right[rightoff + 7] = y2;
        }
        x1 = (x1 + ctrlx1) / 2.0d;
        y1 = (y1 + ctrly1) / 2.0d;
        x2 = (x2 + ctrlx2) / 2.0d;
        y2 = (y2 + ctrly2) / 2.0d;
        double centerx = (ctrlx1 + ctrlx2) / 2.0d;
        double centery = (ctrly1 + ctrly2) / 2.0d;
        ctrlx1 = (x1 + centerx) / 2.0d;
        ctrly1 = (y1 + centery) / 2.0d;
        ctrlx2 = (x2 + centerx) / 2.0d;
        ctrly2 = (y2 + centery) / 2.0d;
        centerx = (ctrlx1 + ctrlx2) / 2.0d;
        centery = (ctrly1 + ctrly2) / 2.0d;
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx1;
            left[leftoff + 5] = ctrly1;
            left[leftoff + 6] = centerx;
            left[leftoff + 7] = centery;
        }
        if (right != null) {
            right[rightoff + 0] = centerx;
            right[rightoff + 1] = centery;
            right[rightoff + 2] = ctrlx2;
            right[rightoff + 3] = ctrly2;
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
    }


    static void subdivideCubicAt(double t, double[] src, int srcoff,
                                 double[] left, int leftoff,
                                 double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx1 = src[srcoff + 2];
        double ctrly1 = src[srcoff + 3];
        double ctrlx2 = src[srcoff + 4];
        double ctrly2 = src[srcoff + 5];
        double x2 = src[srcoff + 6];
        double y2 = src[srcoff + 7];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 6] = x2;
            right[rightoff + 7] = y2;
        }
        x1 = x1 + t * (ctrlx1 - x1);
        y1 = y1 + t * (ctrly1 - y1);
        x2 = ctrlx2 + t * (x2 - ctrlx2);
        y2 = ctrly2 + t * (y2 - ctrly2);
        double centerx = ctrlx1 + t * (ctrlx2 - ctrlx1);
        double centery = ctrly1 + t * (ctrly2 - ctrly1);
        ctrlx1 = x1 + t * (centerx - x1);
        ctrly1 = y1 + t * (centery - y1);
        ctrlx2 = centerx + t * (x2 - centerx);
        ctrly2 = centery + t * (y2 - centery);
        centerx = ctrlx1 + t * (ctrlx2 - ctrlx1);
        centery = ctrly1 + t * (ctrly2 - ctrly1);
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx1;
            left[leftoff + 5] = ctrly1;
            left[leftoff + 6] = centerx;
            left[leftoff + 7] = centery;
        }
        if (right != null) {
            right[rightoff + 0] = centerx;
            right[rightoff + 1] = centery;
            right[rightoff + 2] = ctrlx2;
            right[rightoff + 3] = ctrly2;
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
    }

    static void subdivideQuad(double[] src, int srcoff,
                              double[] left, int leftoff,
                              double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx = src[srcoff + 2];
        double ctrly = src[srcoff + 3];
        double x2 = src[srcoff + 4];
        double y2 = src[srcoff + 5];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
        x1 = (x1 + ctrlx) / 2.0d;
        y1 = (y1 + ctrly) / 2.0d;
        x2 = (x2 + ctrlx) / 2.0d;
        y2 = (y2 + ctrly) / 2.0d;
        ctrlx = (x1 + x2) / 2.0d;
        ctrly = (y1 + y2) / 2.0d;
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx;
            left[leftoff + 5] = ctrly;
        }
        if (right != null) {
            right[rightoff + 0] = ctrlx;
            right[rightoff + 1] = ctrly;
            right[rightoff + 2] = x2;
            right[rightoff + 3] = y2;
        }
    }

    static void subdivideQuadAt(double t, double[] src, int srcoff,
                                double[] left, int leftoff,
                                double[] right, int rightoff)
    {
        double x1 = src[srcoff + 0];
        double y1 = src[srcoff + 1];
        double ctrlx = src[srcoff + 2];
        double ctrly = src[srcoff + 3];
        double x2 = src[srcoff + 4];
        double y2 = src[srcoff + 5];
        if (left != null) {
            left[leftoff + 0] = x1;
            left[leftoff + 1] = y1;
        }
        if (right != null) {
            right[rightoff + 4] = x2;
            right[rightoff + 5] = y2;
        }
        x1 = x1 + t * (ctrlx - x1);
        y1 = y1 + t * (ctrly - y1);
        x2 = ctrlx + t * (x2 - ctrlx);
        y2 = ctrly + t * (y2 - ctrly);
        ctrlx = x1 + t * (x2 - x1);
        ctrly = y1 + t * (y2 - y1);
        if (left != null) {
            left[leftoff + 2] = x1;
            left[leftoff + 3] = y1;
            left[leftoff + 4] = ctrlx;
            left[leftoff + 5] = ctrly;
        }
        if (right != null) {
            right[rightoff + 0] = ctrlx;
            right[rightoff + 1] = ctrly;
            right[rightoff + 2] = x2;
            right[rightoff + 3] = y2;
        }
    }

    static void subdivideAt(double t, double[] src, int srcoff,
                            double[] left, int leftoff,
                            double[] right, int rightoff, int size)
    {
        switch(size) {
        case 8:
            subdivideCubicAt(t, src, srcoff, left, leftoff, right, rightoff);
            return;
        case 6:
            subdivideQuadAt(t, src, srcoff, left, leftoff, right, rightoff);
            return;
        }
    }

    // From sun.java2d.loops.GeneralRenderer:

    static int outcode(final double x, final double y,
                       final double[] clipRect)
    {
        int code;
        if (y < clipRect[0]) {
            code = OUTCODE_TOP;
        } else if (y >= clipRect[1]) {
            code = OUTCODE_BOTTOM;
        } else {
            code = 0;
        }
        if (x < clipRect[2]) {
            code |= OUTCODE_LEFT;
        } else if (x >= clipRect[3]) {
            code |= OUTCODE_RIGHT;
        }
        return code;
    }

    // a stack of polynomial curves where each curve shares endpoints with
    // adjacent ones.
    static final class PolyStack {
        private static final byte TYPE_LINETO  = (byte) 0;
        private static final byte TYPE_QUADTO  = (byte) 1;
        private static final byte TYPE_CUBICTO = (byte) 2;

        // curves capacity = edges count (8192) = edges x 2 (coords)
        private static final int INITIAL_CURVES_COUNT = INITIAL_EDGES_COUNT << 1;

        // types capacity = edges count (4096)
        private static final int INITIAL_TYPES_COUNT = INITIAL_EDGES_COUNT;

        double[] curves;
        int end;
        byte[] curveTypes;
        int numCurves;

        // curves ref (dirty)
        final DoubleArrayCache.Reference curves_ref;
        // curveTypes ref (dirty)
        final ByteArrayCache.Reference curveTypes_ref;

        // used marks (stats only)
        int curveTypesUseMark;
        int curvesUseMark;

        private final StatLong stat_polystack_types;
        private final StatLong stat_polystack_curves;
        private final Histogram hist_polystack_curves;
        private final StatLong stat_array_polystack_curves;
        private final StatLong stat_array_polystack_curveTypes;

        PolyStack(final DRendererContext rdrCtx) {
            this(rdrCtx, null, null, null, null, null);
        }

        PolyStack(final DRendererContext rdrCtx,
                  final StatLong stat_polystack_types,
                  final StatLong stat_polystack_curves,
                  final Histogram hist_polystack_curves,
                  final StatLong stat_array_polystack_curves,
                  final StatLong stat_array_polystack_curveTypes)
        {
            curves_ref = rdrCtx.newDirtyDoubleArrayRef(INITIAL_CURVES_COUNT); // 32K
            curves     = curves_ref.initial;

            curveTypes_ref = rdrCtx.newDirtyByteArrayRef(INITIAL_TYPES_COUNT); // 4K
            curveTypes     = curveTypes_ref.initial;
            numCurves = 0;
            end = 0;

            if (DO_STATS) {
                curveTypesUseMark = 0;
                curvesUseMark = 0;
            }
            this.stat_polystack_types = stat_polystack_types;
            this.stat_polystack_curves = stat_polystack_curves;
            this.hist_polystack_curves = hist_polystack_curves;
            this.stat_array_polystack_curves = stat_array_polystack_curves;
            this.stat_array_polystack_curveTypes = stat_array_polystack_curveTypes;
        }

        /**
         * Disposes this PolyStack:
         * clean up before reusing this instance
         */
        void dispose() {
            end = 0;
            numCurves = 0;

            if (DO_STATS) {
                stat_polystack_types.add(curveTypesUseMark);
                stat_polystack_curves.add(curvesUseMark);
                hist_polystack_curves.add(curvesUseMark);

                // reset marks
                curveTypesUseMark = 0;
                curvesUseMark = 0;
            }

            // Return arrays:
            // curves and curveTypes are kept dirty
            curves     = curves_ref.putArray(curves);
            curveTypes = curveTypes_ref.putArray(curveTypes);
        }

        private void ensureSpace(final int n) {
            // use substraction to avoid integer overflow:
            if (curves.length - end < n) {
                if (DO_STATS) {
                    stat_array_polystack_curves.add(end + n);
                }
                curves = curves_ref.widenArray(curves, end, end + n);
            }
            if (curveTypes.length <= numCurves) {
                if (DO_STATS) {
                    stat_array_polystack_curveTypes.add(numCurves + 1);
                }
                curveTypes = curveTypes_ref.widenArray(curveTypes,
                                                       numCurves,
                                                       numCurves + 1);
            }
        }

        void pushCubic(double x0, double y0,
                       double x1, double y1,
                       double x2, double y2)
        {
            ensureSpace(6);
            curveTypes[numCurves++] = TYPE_CUBICTO;
            // we reverse the coordinate order to make popping easier
            final double[] _curves = curves;
            int e = end;
            _curves[e++] = x2;    _curves[e++] = y2;
            _curves[e++] = x1;    _curves[e++] = y1;
            _curves[e++] = x0;    _curves[e++] = y0;
            end = e;
        }

        void pushQuad(double x0, double y0,
                      double x1, double y1)
        {
            ensureSpace(4);
            curveTypes[numCurves++] = TYPE_QUADTO;
            final double[] _curves = curves;
            int e = end;
            _curves[e++] = x1;    _curves[e++] = y1;
            _curves[e++] = x0;    _curves[e++] = y0;
            end = e;
        }

        void pushLine(double x, double y) {
            ensureSpace(2);
            curveTypes[numCurves++] = TYPE_LINETO;
            curves[end++] = x;    curves[end++] = y;
        }

        void pullAll(final DPathConsumer2D io) {
            final int nc = numCurves;
            if (nc == 0) {
                return;
            }
            if (DO_STATS) {
                // update used marks:
                if (numCurves > curveTypesUseMark) {
                    curveTypesUseMark = numCurves;
                }
                if (end > curvesUseMark) {
                    curvesUseMark = end;
                }
            }
            final byte[]  _curveTypes = curveTypes;
            final double[] _curves = curves;
            int e = 0;

            for (int i = 0; i < nc; i++) {
                switch(_curveTypes[i]) {
                case TYPE_LINETO:
                    io.lineTo(_curves[e], _curves[e+1]);
                    e += 2;
                    continue;
                case TYPE_QUADTO:
                    io.quadTo(_curves[e+0], _curves[e+1],
                              _curves[e+2], _curves[e+3]);
                    e += 4;
                    continue;
                case TYPE_CUBICTO:
                    io.curveTo(_curves[e+0], _curves[e+1],
                               _curves[e+2], _curves[e+3],
                               _curves[e+4], _curves[e+5]);
                    e += 6;
                    continue;
                default:
                }
            }
            numCurves = 0;
            end = 0;
        }

        void popAll(final DPathConsumer2D io) {
            int nc = numCurves;
            if (nc == 0) {
                return;
            }
            if (DO_STATS) {
                // update used marks:
                if (numCurves > curveTypesUseMark) {
                    curveTypesUseMark = numCurves;
                }
                if (end > curvesUseMark) {
                    curvesUseMark = end;
                }
            }
            final byte[]  _curveTypes = curveTypes;
            final double[] _curves = curves;
            int e  = end;

            while (nc != 0) {
                switch(_curveTypes[--nc]) {
                case TYPE_LINETO:
                    e -= 2;
                    io.lineTo(_curves[e], _curves[e+1]);
                    continue;
                case TYPE_QUADTO:
                    e -= 4;
                    io.quadTo(_curves[e+0], _curves[e+1],
                              _curves[e+2], _curves[e+3]);
                    continue;
                case TYPE_CUBICTO:
                    e -= 6;
                    io.curveTo(_curves[e+0], _curves[e+1],
                               _curves[e+2], _curves[e+3],
                               _curves[e+4], _curves[e+5]);
                    continue;
                default:
                }
            }
            numCurves = 0;
            end = 0;
        }

        @Override
        public String toString() {
            String ret = "";
            int nc = numCurves;
            int last = end;
            int len;
            while (nc != 0) {
                switch(curveTypes[--nc]) {
                case TYPE_LINETO:
                    len = 2;
                    ret += "line: ";
                    break;
                case TYPE_QUADTO:
                    len = 4;
                    ret += "quad: ";
                    break;
                case TYPE_CUBICTO:
                    len = 6;
                    ret += "cubic: ";
                    break;
                default:
                    len = 0;
                }
                last -= len;
                ret += Arrays.toString(Arrays.copyOfRange(curves, last, last+len))
                                       + "\n";
            }
            return ret;
        }
    }

    // a stack of integer indices
    static final class IndexStack {

        // integer capacity = edges count / 4 ~ 1024
        private static final int INITIAL_COUNT = INITIAL_EDGES_COUNT >> 2;

        private int end;
        private int[] indices;

        // indices ref (dirty)
        private final IntArrayCache.Reference indices_ref;

        // used marks (stats only)
        private int indicesUseMark;

        private final StatLong stat_idxstack_indices;
        private final Histogram hist_idxstack_indices;
        private final StatLong stat_array_idxstack_indices;

        IndexStack(final DRendererContext rdrCtx) {
            this(rdrCtx, null, null, null);
        }

        IndexStack(final DRendererContext rdrCtx,
                   final StatLong stat_idxstack_indices,
                   final Histogram hist_idxstack_indices,
                   final StatLong stat_array_idxstack_indices)
        {
            indices_ref = rdrCtx.newDirtyIntArrayRef(INITIAL_COUNT); // 4K
            indices     = indices_ref.initial;
            end = 0;

            if (DO_STATS) {
                indicesUseMark = 0;
            }
            this.stat_idxstack_indices = stat_idxstack_indices;
            this.hist_idxstack_indices = hist_idxstack_indices;
            this.stat_array_idxstack_indices = stat_array_idxstack_indices;
        }

        /**
         * Disposes this PolyStack:
         * clean up before reusing this instance
         */
        void dispose() {
            end = 0;

            if (DO_STATS) {
                stat_idxstack_indices.add(indicesUseMark);
                hist_idxstack_indices.add(indicesUseMark);

                // reset marks
                indicesUseMark = 0;
            }

            // Return arrays:
            // values is kept dirty
            indices = indices_ref.putArray(indices);
        }

        boolean isEmpty() {
            return (end == 0);
        }

        void reset() {
            end = 0;
        }

        void push(final int v) {
            // remove redundant values (reverse order):
            int[] _values = indices;
            final int nc = end;
            if (nc != 0) {
                if (_values[nc - 1] == v) {
                    // remove both duplicated values:
                    end--;
                    return;
                }
            }
            if (_values.length <= nc) {
                if (DO_STATS) {
                    stat_array_idxstack_indices.add(nc + 1);
                }
                indices = _values = indices_ref.widenArray(_values, nc, nc + 1);
            }
            _values[end++] = v;

            if (DO_STATS) {
                // update used marks:
                if (end > indicesUseMark) {
                    indicesUseMark = end;
                }
            }
        }

        void pullAll(final double[] points, final DPathConsumer2D io) {
            final int nc = end;
            if (nc == 0) {
                return;
            }
            final int[] _values = indices;

            for (int i = 0, j; i < nc; i++) {
                j = _values[i] << 1;
                io.lineTo(points[j], points[j + 1]);
            }
            end = 0;
        }
    }
}
