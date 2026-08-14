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
 * While armed, one measurement takes exactly two clicks:
 * </p>
 * <ul>
 * <li>Click once to drop the start point. The line then follows the cursor and the label shows the distance to it,
 * updating as the mouse is moved around to pick the end.</li>
 * <li>Click again to close the measurement. Dragging never closes one; only a second click does.</li>
 * <li>The right button abandons a measurement that has only its first point down.</li>
 * </ul>
 * <p>
 * The distance is the geodesic distance between the two points, as the crow flies: it comes from the great-circle
 * angle between their latitudes and longitudes and the globe radius there, so terrain relief does not enter into it.
 * The line itself is drawn on the surface and so appears to ride over the ground between them.
 * </p>
 * <p>
 * A finished measurement stays on the globe with its distance and a red delete button next to its end point. The tool
 * remains armed, so the next measurement starts on the very next click and measurements never chain into one growing
 * line.
 * </p>
 *
 * @author Cursor Agent
 */
public class DistanceMeasureTool extends AbstractMapTool
{
    protected static final Color LINE_COLOR = new Color(30, 120, 220);
    /** Cursor movement below this fraction of the eye altitude does not rebuild the preview. */
    protected static final double ECHO_ALTITUDE_FRACTION = 0.0005;

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
            this.wwd.redraw();
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
        // Take the point where the cursor ended up, so the measurement closes exactly where the echoed line was last
        // drawn. How far the mouse travelled while the button was down does not matter: a measurement is two clicks,
        // never a drag.
        Position endPoint = release != null ? release : this.pressPosition;

        if (this.pressStartedMeasurement)
        {
            // First click: the start point is down, now the line follows the cursor until the closing click.
            this.rubberBandEnd = endPoint;
            this.updatePreview();
            this.wwd.redraw();
        }
        else
        {
            // Second click closes the measurement.
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
        if (position == null || !this.isWorthEchoing(position))
        {
            return;
        }

        this.rubberBandEnd = position;
        // No redraw request here. This callback runs inside WorldWindowGLAutoDrawable.display(), so asking for a
        // repaint would schedule another frame, whose position callback would ask again: the globe would render
        // flat out and the terrain would visibly ripple. The mouse events that move the cursor already trigger a
        // redraw of their own, which is what puts the updated line on screen.
        this.updatePreview();
    }

    /**
     * Indicates whether a new cursor position is far enough from the current rubber-band end to be worth rebuilding
     * the preview. Sub-pixel jitter, which the terrain produces on its own as elevations settle, is ignored.
     *
     * @param position the candidate position.
     *
     * @return true when the preview should be rebuilt.
     */
    protected boolean isWorthEchoing(Position position)
    {
        if (this.rubberBandEnd == null)
        {
            return true;
        }

        double eyeAltitude = this.wwd.getView().getEyePosition() != null
            ? Math.abs(this.wwd.getView().getEyePosition().getElevation()) : 0;
        double threshold = Math.max(0.5, eyeAltitude * ECHO_ALTITUDE_FRACTION);
        return this.computeDistanceMeters(this.rubberBandEnd, position) >= threshold;
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
            return;
        }

        this.previewLine.setLocations(new ArrayList<LatLon>(preview));
        this.previewLine.setVisible(true);

        double meters = this.computePathLengthMeters(preview);
        this.previewLabel.setPosition(midPosition(preview.get(preview.size() - 2), preview.get(preview.size() - 1)));
        this.previewLabel.setText(formatDistance(meters));
        this.previewLabel.getAttributes().setVisible(true);
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
