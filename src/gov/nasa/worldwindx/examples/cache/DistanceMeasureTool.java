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

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.PositionEvent;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfacePolyline;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Google Maps-style distance measuring that keeps as many measurements on the globe as the user wants.
 * <p>
 * While armed, one measurement takes two points and finishes on its own:
 * </p>
 * <ul>
 * <li>Click once to drop the start point, move the mouse to see the line and the running distance, then click again to
 * finish.</li>
 * <li>Or press, drag and release to do both in one gesture.</li>
 * <li>The right button abandons a measurement that has only its first point down.</li>
 * </ul>
 * <p>
 * A finished measurement stays on the globe with its total distance and a red delete button next to its last point.
 * The tool remains armed, so the next measurement starts on the very next click and measurements never chain into one
 * growing line.
 * </p>
 *
 * @author Cursor Agent
 */
public class DistanceMeasureTool extends AbstractMapTool
{
    protected static final Color LINE_COLOR = new Color(30, 120, 220);

    protected final SurfacePolyline previewLine;
    protected final GlobeAnnotation previewLabel;

    protected final List<Position> vertices = new ArrayList<Position>();
    protected Position rubberBandEnd;
    protected Position pressPosition;
    protected Point pressScreenPoint;
    protected boolean measuring;
    protected boolean pressStartedMeasurement;

    public DistanceMeasureTool(MapOverlayManager manager)
    {
        super(manager);

        this.previewLine = createLine(LINE_COLOR, 3);
        this.previewLine.setVisible(false);

        this.previewLabel = new GlobeAnnotation("", Position.ZERO, createLabelAttributes(LINE_COLOR));
        this.previewLabel.setAlwaysOnTop(true);
        this.previewLabel.setPickEnabled(false);
        this.previewLabel.getAttributes().setVisible(false);

        this.manager.getShapeLayer().addRenderable(this.previewLine);
        this.manager.getShapeLayer().addRenderable(this.previewLabel);
    }

    /**
     * Returns the number of finished measurements currently on the globe.
     *
     * @return the measurement count.
     */
    public int getMeasurementCount()
    {
        return this.manager.getOverlayCount(MapOverlay.MEASUREMENT);
    }

    /** Removes every finished measurement from the globe. */
    public void clearMeasurements()
    {
        this.manager.removeOverlays(MapOverlay.MEASUREMENT);
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (!this.armed || e.isConsumed() || this.isOverOverlayControl())
        {
            return;
        }

        if (e.getButton() == MouseEvent.BUTTON3)
        {
            // Right button abandons a measurement whose first point is already down.
            if (this.measuring)
            {
                this.cancelInProgressWork();
                e.consume();
            }
            return;
        }

        if (e.getButton() != MouseEvent.BUTTON1)
        {
            return;
        }

        this.pressScreenPoint = e.getPoint();
        this.pressPosition = this.wwd.getCurrentPosition();

        if (this.pressPosition != null)
        {
            // Start on the press, not on the release, so the line and its distance follow the cursor from the very
            // first button-down instead of appearing only once the button comes back up.
            this.pressStartedMeasurement = !this.measuring;
            if (this.pressStartedMeasurement)
            {
                this.measuring = true;
                this.vertices.clear();
                this.vertices.add(this.pressPosition);
            }
            this.rubberBandEnd = this.pressPosition;
            this.updatePreview();
        }

        e.consume();
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (!this.armed || !this.measuring || this.pressPosition == null || e.isConsumed())
        {
            return;
        }

        Position position = this.wwd.getCurrentPosition();
        if (position != null)
        {
            this.rubberBandEnd = position;
            this.updatePreview();
        }
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (!this.armed || e.getButton() != MouseEvent.BUTTON1 || this.pressPosition == null || e.isConsumed())
        {
            return;
        }

        Position release = this.wwd.getCurrentPosition();
        boolean dragged = this.isDrag(this.pressScreenPoint, e.getPoint());
        Position endPoint = dragged && release != null ? release : this.pressPosition;

        if (this.pressStartedMeasurement && !dragged)
        {
            // Plain first click: keep the start point on the globe and wait for the closing click.
            this.rubberBandEnd = endPoint;
            this.updatePreview();
        }
        else
        {
            // Either the press-drag-release gesture or the second click. Two points always close a measurement, so
            // they never chain into one growing line.
            this.addVertex(endPoint);
            this.finishMeasurement();
        }

        this.pressPosition = null;
        this.pressScreenPoint = null;
        e.consume();
    }

    @Override
    public void moved(PositionEvent event)
    {
        if (!this.armed || !this.measuring)
        {
            return;
        }

        Position position = event.getPosition();
        if (position == null)
        {
            return;
        }

        this.rubberBandEnd = position;
        this.updatePreview();
    }

    protected void addVertex(Position position)
    {
        if (position == null)
        {
            return;
        }

        // A double-click delivers two press-release pairs at the same spot; do not record the point twice.
        if (!this.vertices.isEmpty() && this.vertices.get(this.vertices.size() - 1).equals(position))
        {
            return;
        }

        this.vertices.add(position);
    }

    protected void updatePreview()
    {
        if (!this.measuring || this.vertices.isEmpty())
        {
            this.previewLine.setVisible(false);
            this.previewLabel.getAttributes().setVisible(false);
            this.wwd.redraw();
            return;
        }

        List<Position> preview = new ArrayList<Position>(this.vertices);
        if (this.rubberBandEnd != null && !preview.get(preview.size() - 1).equals(this.rubberBandEnd))
        {
            preview.add(this.rubberBandEnd);
        }

        if (preview.size() < 2)
        {
            this.previewLine.setVisible(false);
            this.previewLabel.getAttributes().setVisible(false);
            this.wwd.redraw();
            return;
        }

        this.previewLine.setLocations(new ArrayList<LatLon>(preview));
        this.previewLine.setVisible(true);

        double meters = this.computePathLengthMeters(preview);
        this.previewLabel.setPosition(midPosition(preview.get(preview.size() - 2), preview.get(preview.size() - 1)));
        this.previewLabel.setText(formatDistance(meters));
        this.previewLabel.getAttributes().setVisible(true);

        this.wwd.redraw();
    }

    protected void finishMeasurement()
    {
        if (this.vertices.size() < 2)
        {
            this.cancelInProgressWork();
            return;
        }

        List<Position> positions = new ArrayList<Position>(this.vertices);
        double meters = this.computePathLengthMeters(positions);
        Position last = positions.get(positions.size() - 1);

        MapOverlay overlay = new MapOverlay(MapOverlay.MEASUREMENT, last);

        SurfacePolyline line = createLine(LINE_COLOR, 3);
        line.setLocations(new ArrayList<LatLon>(positions));
        overlay.addRenderable(line);

        GlobeAnnotation label = new GlobeAnnotation(formatDistance(meters),
            midPosition(positions.get(positions.size() - 2), last), createLabelAttributes(LINE_COLOR));
        label.setAlwaysOnTop(true);
        label.setPickEnabled(false);
        overlay.addRenderable(label);

        overlay.setDescription(formatDistance(meters));
        this.manager.addOverlay(overlay);

        this.cancelInProgressWork();
    }

    @Override
    protected void cancelInProgressWork()
    {
        this.measuring = false;
        this.pressStartedMeasurement = false;
        this.vertices.clear();
        this.rubberBandEnd = null;
        this.pressPosition = null;
        this.pressScreenPoint = null;
        this.previewLine.setVisible(false);
        this.previewLine.setLocations(new ArrayList<LatLon>());
        this.previewLabel.setText("");
        this.previewLabel.getAttributes().setVisible(false);
        this.wwd.redraw();
    }

    /**
     * Builds the line used for measurements and drawings.
     * <p>
     * A surface shape is used rather than a terrain-following {@link gov.nasa.worldwind.render.Path}. A path with
     * CLAMP_TO_GROUND and follow-terrain re-tessellates itself against the terrain every time its positions change, and
     * the subdivision count grows as the eye descends, so echoing one under the cursor stalls rendering when zoomed in.
     * A surface polyline is painted into the surface tiles instead: its cost is bounded by screen area, and the terrain
     * can never hide it. WorldWind's own MeasureTool defaults to follow-terrain off for the same reason.
     * </p>
     *
     * @param color line color.
     * @param width line width in pixels.
     *
     * @return a line with no locations yet.
     */
    protected static SurfacePolyline createLine(Color color, double width)
    {
        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(new Material(color));
        attrs.setOutlineWidth(width);
        attrs.setOutlineOpacity(0.95);
        attrs.setDrawOutline(true);
        attrs.setDrawInterior(false);

        SurfacePolyline line = new SurfacePolyline(attrs);
        line.setPathType(AVKey.GREAT_CIRCLE);
        return line;
    }
}
