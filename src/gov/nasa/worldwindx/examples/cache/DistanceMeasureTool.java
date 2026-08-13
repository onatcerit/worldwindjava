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

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.PositionEvent;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Path;
import gov.nasa.worldwind.render.ShapeAttributes;

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

    protected final Path previewPath;
    protected final GlobeAnnotation previewLabel;

    protected final List<Position> vertices = new ArrayList<Position>();
    protected Position rubberBandEnd;
    protected Position pressPosition;
    protected Point pressScreenPoint;
    protected boolean measuring;

    public DistanceMeasureTool(MapOverlayManager manager)
    {
        super(manager);

        this.previewPath = createPath(LINE_COLOR, 3);
        this.previewPath.setVisible(false);

        this.previewLabel = new GlobeAnnotation("", Position.ZERO, createLabelAttributes(LINE_COLOR));
        this.previewLabel.setAlwaysOnTop(true);
        this.previewLabel.setPickEnabled(false);
        this.previewLabel.getAttributes().setVisible(false);

        this.manager.getShapeLayer().addRenderable(this.previewPath);
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
        e.consume();
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (!this.armed || this.pressPosition == null || e.isConsumed())
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

        if (!this.measuring)
        {
            // First point of a new measurement. A drag completes it in one gesture; a plain click waits for the
            // second click.
            this.measuring = true;
            this.vertices.clear();
            this.vertices.add(this.pressPosition);
            this.rubberBandEnd = endPoint;

            if (dragged && release != null)
            {
                this.addVertex(release);
                this.finishMeasurement();
            }
            else
            {
                this.updatePreview();
            }
        }
        else
        {
            // Second point closes the measurement, so measurements never chain into one endless line.
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
            this.previewPath.setVisible(false);
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
            this.previewPath.setVisible(false);
            this.previewLabel.getAttributes().setVisible(false);
            this.wwd.redraw();
            return;
        }

        this.previewPath.setPositions(preview);
        this.previewPath.setVisible(true);

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

        Path path = createPath(LINE_COLOR, 3);
        path.setPositions(positions);
        path.setShowPositions(true);
        path.setShowPositionsScale(4);
        overlay.addRenderable(path);

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
        this.vertices.clear();
        this.rubberBandEnd = null;
        this.pressPosition = null;
        this.pressScreenPoint = null;
        this.previewPath.setVisible(false);
        this.previewPath.setPositions(new ArrayList<Position>());
        this.previewLabel.setText("");
        this.previewLabel.getAttributes().setVisible(false);
        this.wwd.redraw();
    }

    protected static Path createPath(Color color, double width)
    {
        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(new Material(color));
        attrs.setOutlineWidth(width);
        attrs.setOutlineOpacity(0.95);
        attrs.setDrawOutline(true);
        attrs.setDrawInterior(false);

        Path path = new Path();
        path.setAttributes(attrs);
        path.setPathType(AVKey.GREAT_CIRCLE);
        // Lay the line on the map so it drapes over hills instead of cutting through them. These two calls are the
        // body of Path.setSurfacePath(true), spelled out because that convenience method does not exist in
        // WorldWind 2.1.0.
        path.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        path.setFollowTerrain(true);
        return path;
    }
}
