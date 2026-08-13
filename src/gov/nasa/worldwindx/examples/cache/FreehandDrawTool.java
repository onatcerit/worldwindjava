/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */
package gov.nasa.worldwindx.examples.cache;

import gov.nasa.worldwind.event.PositionEvent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfacePolygon;
import gov.nasa.worldwind.render.SurfacePolyline;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Freehand drawing on the globe, as if sketching a border with a pen.
 * <p>
 * While armed, pressing and dragging traces the cursor across the terrain. Releasing the button finishes the stroke. In
 * closed mode the stroke becomes a filled area whose perimeter and area are labelled, which is the mode to use for
 * drawing a border; otherwise it stays an open line labelled with its length. Every finished drawing carries a red
 * delete button on the map, and the tool stays armed so more can be drawn.
 * </p>
 *
 * @author Cursor Agent
 */
public class FreehandDrawTool extends AbstractMapTool
{
    protected static final Color DRAW_COLOR = new Color(220, 60, 160);
    /** Upper bound on vertices per stroke, so a slow drag cannot build an unbounded shape. */
    protected static final int MAX_STROKE_POSITIONS = 4000;
    /** Spacing between recorded vertices, as a fraction of the eye altitude. */
    protected static final double SPACING_ALTITUDE_FRACTION = 0.001;

    protected final SurfacePolyline previewLine;
    protected final GlobeAnnotation previewLabel;
    protected final List<Position> stroke = new ArrayList<Position>();

    protected boolean drawing;
    protected boolean closed = true;
    protected int nextDrawingNumber = 1;

    public FreehandDrawTool(MapOverlayManager manager)
    {
        super(manager);

        this.previewLine = DistanceMeasureTool.createLine(DRAW_COLOR, 3);
        this.previewLine.setVisible(false);

        this.previewLabel = new GlobeAnnotation("", Position.ZERO, createLabelAttributes(DRAW_COLOR));
        this.previewLabel.setAlwaysOnTop(true);
        this.previewLabel.setPickEnabled(false);
        this.previewLabel.getAttributes().setVisible(false);

        this.manager.getShapeLayer().addRenderable(this.previewLine);
        this.manager.getShapeLayer().addRenderable(this.previewLabel);
    }

    /**
     * Indicates whether finished strokes are closed into a filled area.
     *
     * @return true when strokes become closed borders.
     */
    public boolean isClosed()
    {
        return this.closed;
    }

    /**
     * Sets whether finished strokes are closed into a filled area.
     *
     * @param closed true to close strokes into borders, false to keep them as open lines.
     */
    public void setClosed(boolean closed)
    {
        this.closed = closed;
    }

    /**
     * Returns the number of drawings currently on the globe.
     *
     * @return the drawing count.
     */
    public int getDrawingCount()
    {
        return this.manager.getOverlayCount(MapOverlay.DRAWING);
    }

    /** Removes every drawing from the globe and restarts the numbering. */
    public void clearDrawings()
    {
        this.manager.removeOverlays(MapOverlay.DRAWING);
        this.nextDrawingNumber = 1;
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (!this.armed || e.getButton() != MouseEvent.BUTTON1 || e.isConsumed() || this.isOverOverlayControl())
        {
            return;
        }

        this.stroke.clear();
        Position position = this.wwd.getCurrentPosition();
        if (position != null)
        {
            this.stroke.add(new Position(position, 0));
        }
        this.drawing = true;
        e.consume();
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (!this.armed || !this.drawing || e.isConsumed())
        {
            return;
        }

        // Positions are collected from the position listener, which resolves the terrain point for each frame. Consume
        // the drag so the view does not move while the pen is down.
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (!this.armed || e.getButton() != MouseEvent.BUTTON1 || !this.drawing || e.isConsumed())
        {
            return;
        }

        Position position = this.wwd.getCurrentPosition();
        if (position != null)
        {
            this.appendPosition(new Position(position, 0), 0);
        }

        this.finishDrawing();
        e.consume();
    }

    @Override
    public void moved(PositionEvent event)
    {
        if (!this.armed || !this.drawing)
        {
            return;
        }

        Position position = event.getPosition();
        if (position == null)
        {
            return;
        }

        if (this.appendPosition(new Position(position, 0), this.minimumSpacingMeters()))
        {
            // Deliberately no redraw request: this callback runs inside the render loop, and asking for a repaint
            // from here makes the globe render continuously and the terrain ripple.
            this.updatePreview();
        }
    }

    /**
     * Adds a vertex to the stroke in progress when it is far enough from the previous one.
     *
     * @param position   the candidate vertex.
     * @param minSpacing minimum distance in meters from the previous vertex.
     *
     * @return true when the vertex was added.
     */
    protected boolean appendPosition(Position position, double minSpacing)
    {
        if (this.stroke.size() >= MAX_STROKE_POSITIONS)
        {
            return false;
        }

        if (this.stroke.isEmpty())
        {
            this.stroke.add(position);
            return true;
        }

        Position last = this.stroke.get(this.stroke.size() - 1);
        if (last.equals(position))
        {
            return false;
        }
        if (minSpacing > 0 && this.computeDistanceMeters(last, position) < minSpacing)
        {
            return false;
        }

        this.stroke.add(position);
        return true;
    }

    protected double minimumSpacingMeters()
    {
        double eyeAltitude = this.wwd.getView().getEyePosition() != null
            ? this.wwd.getView().getEyePosition().getElevation() : 0;
        return Math.max(1.0, Math.abs(eyeAltitude) * SPACING_ALTITUDE_FRACTION);
    }

    protected void updatePreview()
    {
        if (this.stroke.size() < 2)
        {
            this.previewLine.setVisible(false);
            this.previewLabel.getAttributes().setVisible(false);
            return;
        }

        this.previewLine.setLocations(new ArrayList<LatLon>(this.stroke));
        this.previewLine.setVisible(true);

        this.previewLabel.setPosition(this.stroke.get(this.stroke.size() - 1));
        this.previewLabel.setText(formatDistance(this.computePathLengthMeters(this.stroke)));
        this.previewLabel.getAttributes().setVisible(true);
    }

    protected void finishDrawing()
    {
        if (this.stroke.size() < 2)
        {
            this.cancelInProgressWork();
            return;
        }

        List<Position> positions = new ArrayList<Position>(this.stroke);
        boolean makeClosed = this.closed && positions.size() >= 3;
        double lengthMeters = this.computePathLengthMeters(positions);

        Position first = positions.get(0);
        MapOverlay overlay = new MapOverlay(MapOverlay.DRAWING, first);
        overlay.getDeleteControl().getAttributes().setDrawOffset(new Point(0, 24));

        Renderable shape;
        String text;
        if (makeClosed)
        {
            lengthMeters += this.computeDistanceMeters(positions.get(positions.size() - 1), first);
            SurfacePolygon polygon = new SurfacePolygon(new ArrayList<LatLon>(positions));
            polygon.setAttributes(createAreaAttributes());
            shape = polygon;

            double area = polygon.getArea(this.wwd.getModel().getGlobe());
            text = "D" + this.nextDrawingNumber++ + "  " + formatDistance(lengthMeters) + "  |  " + formatArea(area);
        }
        else
        {
            SurfacePolyline line = DistanceMeasureTool.createLine(DRAW_COLOR, 3);
            line.setLocations(new ArrayList<LatLon>(positions));
            shape = line;
            text = "D" + this.nextDrawingNumber++ + "  " + formatDistance(lengthMeters);
        }

        overlay.addRenderable(shape);

        GlobeAnnotation label = new GlobeAnnotation(text, first, createLabelAttributes(DRAW_COLOR));
        label.setAlwaysOnTop(true);
        label.setPickEnabled(false);
        label.getAttributes().setDrawOffset(new Point(0, -18));
        overlay.addRenderable(label);

        overlay.setDescription(text);
        this.manager.addOverlay(overlay);

        this.cancelInProgressWork();
    }

    @Override
    protected void cancelInProgressWork()
    {
        this.drawing = false;
        this.stroke.clear();
        this.previewLine.setVisible(false);
        this.previewLine.setLocations(new ArrayList<LatLon>());
        this.previewLabel.setText("");
        this.previewLabel.getAttributes().setVisible(false);
        this.wwd.redraw();
    }

    protected static ShapeAttributes createAreaAttributes()
    {
        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setDrawInterior(true);
        attrs.setInteriorMaterial(new Material(DRAW_COLOR));
        attrs.setInteriorOpacity(0.22);
        attrs.setDrawOutline(true);
        attrs.setOutlineMaterial(new Material(DRAW_COLOR));
        attrs.setOutlineWidth(3);
        attrs.setOutlineOpacity(0.95);
        return attrs;
    }
}
