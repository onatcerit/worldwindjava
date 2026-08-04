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

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.PositionEvent;
import gov.nasa.worldwind.event.PositionListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Path;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwindx.examples.ApplicationTemplate;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;

/**
 * Google Maps-style distance measure tool.
 * <p>
 * When armed, the user presses on the globe to set a start point, then drags to stretch a line. The geodesic distance
 * from the start point is displayed in a label centered on the line and updates continuously while dragging.
 * </p>
 *
 * @author Cursor Agent
 */
public class DistanceMeasureTool extends MouseAdapter implements MouseMotionListener, PositionListener
{
    protected final WorldWindow wwd;
    protected final RenderableLayer layer;
    protected final Path path;
    protected final GlobeAnnotation distanceLabel;
    protected final ShapeAttributes pathAttributes;

    protected boolean armed;
    protected boolean dragging;
    protected Position startPosition;
    protected Position endPosition;
    protected Runnable armedStateListener;

    public DistanceMeasureTool(WorldWindow wwd)
    {
        if (wwd == null)
        {
            throw new IllegalArgumentException("WorldWindow is required");
        }

        this.wwd = wwd;

        this.pathAttributes = new BasicShapeAttributes();
        this.pathAttributes.setOutlineMaterial(new Material(new Color(30, 120, 220)));
        this.pathAttributes.setOutlineWidth(3);
        this.pathAttributes.setOutlineOpacity(0.95);
        this.pathAttributes.setDrawOutline(true);
        this.pathAttributes.setDrawInterior(false);

        this.path = new Path();
        this.path.setAttributes(this.pathAttributes);
        this.path.setPathType(AVKey.GREAT_CIRCLE);
        this.path.setFollowTerrain(true);
        this.path.setTerrainConformance(5);
        this.path.setVisible(false);

        AnnotationAttributes labelAttrs = new AnnotationAttributes();
        labelAttrs.setFrameShape(AVKey.SHAPE_RECTANGLE);
        labelAttrs.setInsets(new Insets(5, 8, 5, 8));
        labelAttrs.setDrawOffset(new Point(0, 18));
        labelAttrs.setBackgroundColor(new Color(255, 255, 255, 230));
        labelAttrs.setTextColor(Color.BLACK);
        labelAttrs.setBorderColor(new Color(30, 120, 220));
        labelAttrs.setBorderWidth(1.5);
        labelAttrs.setCornerRadius(6);
        labelAttrs.setFont(Font.decode("Arial-BOLD-14"));
        labelAttrs.setEffect(AVKey.TEXT_EFFECT_NONE);
        labelAttrs.setLeader(AVKey.SHAPE_NONE);
        labelAttrs.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);

        this.distanceLabel = new GlobeAnnotation("", Position.ZERO, labelAttrs);
        this.distanceLabel.setAlwaysOnTop(true);
        this.distanceLabel.setPickEnabled(false);
        this.distanceLabel.getAttributes().setVisible(false);

        this.layer = new RenderableLayer();
        this.layer.setName("Distance Measure");
        this.layer.setPickEnabled(false);
        this.layer.addRenderable(this.path);
        this.layer.addRenderable(this.distanceLabel);
        ApplicationTemplate.insertBeforeCompass(this.wwd, this.layer);

        this.wwd.getInputHandler().addMouseListener(this);
        this.wwd.getInputHandler().addMouseMotionListener(this);
        this.wwd.addPositionListener(this);
    }

    public boolean isArmed()
    {
        return this.armed;
    }

    public void setArmedStateListener(Runnable armedStateListener)
    {
        this.armedStateListener = armedStateListener;
    }

    public void setArmed(boolean armed)
    {
        if (this.armed == armed)
        {
            return;
        }

        this.armed = armed;
        this.dragging = false;

        if (armed)
        {
            this.clearMeasurement();
            ((Component) this.wwd).setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }
        else
        {
            ((Component) this.wwd).setCursor(Cursor.getDefaultCursor());
        }

        this.notifyArmedStateChanged();
        this.wwd.redraw();
    }

    public void toggleArmed()
    {
        this.setArmed(!this.armed);
    }

    public void clearMeasurement()
    {
        this.startPosition = null;
        this.endPosition = null;
        this.dragging = false;
        this.path.setVisible(false);
        this.path.setPositions(new ArrayList<Position>());
        this.distanceLabel.getAttributes().setVisible(false);
        this.distanceLabel.setText("");
        this.wwd.redraw();
    }

    public double getDistanceMeters()
    {
        if (this.startPosition == null || this.endPosition == null)
        {
            return -1;
        }
        return this.computeDistanceMeters(this.startPosition, this.endPosition);
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (!this.armed || e.getButton() != MouseEvent.BUTTON1 || e.isConsumed())
        {
            return;
        }

        Position position = this.wwd.getCurrentPosition();
        if (position == null)
        {
            return;
        }

        this.startPosition = position;
        this.endPosition = position;
        this.dragging = true;
        this.updateGraphics();
        e.consume();
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (!this.armed || !this.dragging || e.isConsumed())
        {
            return;
        }

        Position position = this.wwd.getCurrentPosition();
        if (position == null)
        {
            return;
        }

        this.endPosition = position;
        this.updateGraphics();
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (!this.armed || e.getButton() != MouseEvent.BUTTON1 || e.isConsumed())
        {
            return;
        }

        if (this.dragging)
        {
            Position position = this.wwd.getCurrentPosition();
            if (position != null)
            {
                this.endPosition = position;
                this.updateGraphics();
            }
            this.dragging = false;
            // Keep the finished measurement visible; disarm so the next interaction can pan the globe.
            this.setArmed(false);
            e.consume();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e)
    {
        // Required by MouseMotionListener; unused while not dragging.
    }

    public void moved(PositionEvent event)
    {
        if (!this.armed || !this.dragging)
        {
            return;
        }

        Position position = event.getPosition();
        if (position == null)
        {
            return;
        }

        this.endPosition = position;
        this.updateGraphics();
    }

    protected void updateGraphics()
    {
        if (this.startPosition == null || this.endPosition == null)
        {
            this.path.setVisible(false);
            this.distanceLabel.getAttributes().setVisible(false);
            return;
        }

        ArrayList<Position> positions = new ArrayList<Position>(2);
        positions.add(this.startPosition);
        positions.add(this.endPosition);
        this.path.setPositions(positions);
        this.path.setVisible(true);

        double meters = this.computeDistanceMeters(this.startPosition, this.endPosition);
        Position mid = this.computeMidPosition(this.startPosition, this.endPosition);
        this.distanceLabel.setPosition(mid);
        this.distanceLabel.setText(formatDistance(meters));
        this.distanceLabel.getAttributes().setVisible(true);

        this.wwd.redraw();
    }

    protected double computeDistanceMeters(Position start, Position end)
    {
        Angle distance = LatLon.greatCircleDistance(start, end);
        double radius = this.wwd.getModel().getGlobe().getRadiusAt(start);
        return distance.radians * radius;
    }

    protected Position computeMidPosition(Position start, Position end)
    {
        LatLon mid = LatLon.interpolateGreatCircle(0.5, start, end);
        double elevation = 0.5 * (start.getElevation() + end.getElevation());
        return new Position(mid, elevation);
    }

    public static String formatDistance(double meters)
    {
        if (meters < 0)
        {
            return "-";
        }
        if (meters < 1000)
        {
            return String.format("%.0f m", meters);
        }
        if (meters < 10000)
        {
            return String.format("%.2f km", meters / 1000.0);
        }
        return String.format("%.1f km", meters / 1000.0);
    }

    protected void notifyArmedStateChanged()
    {
        if (this.armedStateListener != null)
        {
            this.armedStateListener.run();
        }
    }
}
