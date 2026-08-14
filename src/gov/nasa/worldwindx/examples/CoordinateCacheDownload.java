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
package gov.nasa.worldwindx.examples;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.terrain.ZeroElevationModel;
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwindx.examples.cache.AbstractMapTool;
import gov.nasa.worldwindx.examples.cache.CoordinateCacheDialog;
import gov.nasa.worldwindx.examples.cache.DistanceMeasureTool;
import gov.nasa.worldwindx.examples.cache.FreehandDrawTool;
import gov.nasa.worldwindx.examples.cache.MapOverlayManager;
import gov.nasa.worldwindx.examples.cache.MapSectorCacheTool;
import gov.nasa.worldwindx.examples.cache.PointMarkerTool;
import gov.nasa.worldwindx.examples.cache.SectorCacheController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Demonstrates offline cache preparation together with the on-globe measuring, point and drawing tools.
 * <p>
 * Lower-left buttons provide:
 * </p>
 * <ul>
 * <li>Manual coordinate entry through {@link CoordinateCacheDialog}</li>
 * <li>Interactive map selection through {@link MapSectorCacheTool}</li>
 * <li>Distance measuring through {@link DistanceMeasureTool}, several measurements at a time</li>
 * <li>Point placement through {@link PointMarkerTool}</li>
 * <li>Freehand border drawing through {@link FreehandDrawTool}</li>
 * </ul>
 * <p>
 * Everything placed on the globe carries its own red delete button, so items can be removed one by one without leaving
 * the map. The Clear map button removes them all at once.
 * </p>
 *
 * @author Cursor Agent
 */
public class CoordinateCacheDownload extends ApplicationTemplate
{
    public static class AppFrame extends ApplicationTemplate.AppFrame
    {
        protected SectorCacheController cacheController;
        protected CoordinateCacheDialog cacheDialog;
        protected MapSectorCacheTool mapCacheTool;
        protected MapOverlayManager overlayManager;
        protected DistanceMeasureTool distanceMeasureTool;
        protected PointMarkerTool pointMarkerTool;
        protected FreehandDrawTool freehandDrawTool;

        protected JButton coordinateButton;
        protected JButton mapButton;
        protected JButton measureButton;
        protected JButton pointButton;
        protected JButton drawButton;
        protected JCheckBox closedShapeCheckBox;
        protected JCheckBox flatTerrainCheckBox;
        protected JButton clearButton;
        protected ElevationModel terrainElevationModel;
        protected JPanel buttonStack;

        public AppFrame()
        {
            this.cacheController = new SectorCacheController(this.getWwd());
            this.cacheDialog = new CoordinateCacheDialog(this, this.cacheController);
            this.mapCacheTool = new MapSectorCacheTool(this, this.cacheController);
            this.mapCacheTool.setSelectionStateListener(new Runnable()
            {
                public void run()
                {
                    updateButtonLabels();
                }
            });
            this.mapCacheTool.setSectorConsumer(new MapSectorCacheTool.SectorConsumer()
            {
                public void accept(Sector sector)
                {
                    // Map selection and coordinate entry share the same download dialog.
                    AppFrame.this.openCacheDialogWithSector(sector);
                }
            });

            this.overlayManager = new MapOverlayManager(this.getWwd());
            this.overlayManager.setChangeListener(new Runnable()
            {
                public void run()
                {
                    updateButtonLabels();
                }
            });

            Runnable armedListener = new Runnable()
            {
                public void run()
                {
                    updateButtonLabels();
                }
            };
            this.distanceMeasureTool = new DistanceMeasureTool(this.overlayManager);
            this.distanceMeasureTool.setArmedStateListener(armedListener);
            this.pointMarkerTool = new PointMarkerTool(this.overlayManager);
            this.pointMarkerTool.setArmedStateListener(armedListener);
            this.freehandDrawTool = new FreehandDrawTool(this.overlayManager);
            this.freehandDrawTool.setArmedStateListener(armedListener);

            this.installBottomLeftCacheButtons();
            this.updateButtonLabels();

            Dimension size = new Dimension(1200, 800);
            this.setPreferredSize(size);
            this.pack();
            WWUtil.alignComponent(null, this, AVKey.CENTER);
        }

        /**
         * Places non-blocking glass-pane buttons over the lower-left corner of the WorldWindow.
         * Clicks outside the buttons pass through to the globe and other UI.
         */
        protected void installBottomLeftCacheButtons()
        {
            this.coordinateButton = this.createStackButton("Cache by Coordinates",
                "Enter coordinates and download the area into the local cache", new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        openCoordinateDialog();
                    }
                });

            this.mapButton = this.createStackButton("Cache from Map",
                "Press, then drag on the globe; selected coordinates open in the cache dialog", new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        toggleMapSelection();
                    }
                });

            this.measureButton = this.createStackButton("Measure Distance",
                "Click once for the start point, move the mouse to see the distance, click again to finish",
                new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        armTool(distanceMeasureTool);
                    }
                });

            this.pointButton = this.createStackButton("Add Point",
                "Click on the globe to drop numbered points", new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        armTool(pointMarkerTool);
                    }
                });

            this.drawButton = this.createStackButton("Draw Border",
                "Press and drag to sketch freehand, like drawing with a pen", new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        armTool(freehandDrawTool);
                    }
                });

            this.closedShapeCheckBox = new JCheckBox("Close the shape", true);
            this.closedShapeCheckBox.setToolTipText(
                "Join the last point back to the first, turning the sketch into a filled border");
            this.closedShapeCheckBox.setFocusable(false);
            this.closedShapeCheckBox.setOpaque(true);
            this.closedShapeCheckBox.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    freehandDrawTool.setClosed(closedShapeCheckBox.isSelected());
                }
            });

            this.flatTerrainCheckBox = new JCheckBox("Flat terrain", false);
            this.flatTerrainCheckBox.setToolTipText(
                "Replace the elevation model with a flat one. Use this if the terrain smears into streaks.");
            this.flatTerrainCheckBox.setFocusable(false);
            this.flatTerrainCheckBox.setOpaque(true);
            this.flatTerrainCheckBox.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    setFlatTerrain(flatTerrainCheckBox.isSelected());
                }
            });

            this.clearButton = this.createStackButton("Clear Map",
                "Remove every measurement, point and drawing from the globe", new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        clearMapItems();
                    }
                });

            this.buttonStack = new JPanel();
            this.buttonStack.setOpaque(false);
            this.buttonStack.setLayout(new BoxLayout(this.buttonStack, BoxLayout.Y_AXIS));

            Component[] stackItems = new Component[] {this.coordinateButton, this.mapButton, this.measureButton,
                this.pointButton, this.drawButton, this.closedShapeCheckBox, this.flatTerrainCheckBox,
                this.clearButton};
            for (int i = 0; i < stackItems.length; i++)
            {
                if (i > 0)
                {
                    this.buttonStack.add(Box.createVerticalStrut(6));
                }
                stackItems[i].setMaximumSize(
                    new Dimension(Integer.MAX_VALUE, stackItems[i].getPreferredSize().height));
                ((JComponent) stackItems[i]).setAlignmentX(Component.LEFT_ALIGNMENT);
                this.buttonStack.add(stackItems[i]);
            }

            final JPanel glass = new JPanel(null)
            {
                @Override
                public boolean contains(int x, int y)
                {
                    return isInsideButtonStack(this, x, y);
                }
            };
            glass.setOpaque(false);
            glass.add(this.buttonStack);

            this.setGlassPane(glass);
            glass.setVisible(true);

            final Component worldWindow = (Component) this.getWwd();
            ComponentAdapter repositionListener = new ComponentAdapter()
            {
                public void componentResized(ComponentEvent e)
                {
                    repositionCacheButtons(glass, worldWindow);
                }

                public void componentMoved(ComponentEvent e)
                {
                    repositionCacheButtons(glass, worldWindow);
                }

                public void componentShown(ComponentEvent e)
                {
                    repositionCacheButtons(glass, worldWindow);
                }
            };
            worldWindow.addComponentListener(repositionListener);
            this.getWwjPanel().addComponentListener(repositionListener);
            this.addComponentListener(repositionListener);

            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    repositionCacheButtons(glass, worldWindow);
                }
            });
        }

        protected JButton createStackButton(String text, String toolTip, ActionListener listener)
        {
            JButton button = new JButton(text);
            button.setToolTipText(toolTip);
            button.setFocusable(false);
            button.addActionListener(listener);
            return button;
        }

        protected boolean isInsideButtonStack(Component glass, int x, int y)
        {
            if (this.buttonStack == null || !this.buttonStack.isShowing())
            {
                return false;
            }
            Point p = SwingUtilities.convertPoint(glass, new Point(x, y), this.buttonStack);
            return this.buttonStack.contains(p);
        }

        protected void repositionCacheButtons(JPanel glass, Component worldWindow)
        {
            if (glass == null || this.buttonStack == null || worldWindow == null || !worldWindow.isShowing())
            {
                return;
            }

            Dimension stackSize = this.buttonStack.getPreferredSize();
            Point worldWindowOrigin = SwingUtilities.convertPoint(worldWindow.getParent(),
                worldWindow.getLocation(), glass);

            // Keep the buttons in the WorldWindow's lower-left corner, above the on-canvas view controls.
            int margin = 12;
            int liftAboveViewControls = 96;
            int x = worldWindowOrigin.x + margin;
            int y = worldWindowOrigin.y + worldWindow.getHeight() - stackSize.height - margin - liftAboveViewControls;

            y = Math.max(worldWindowOrigin.y + margin, y);
            this.buttonStack.setBounds(x, y, stackSize.width, stackSize.height);
            glass.revalidate();
            glass.repaint();
        }

        /**
         * Arms one globe tool and disarms everything else, so a click on the globe only ever means one thing.
         *
         * @param tool the tool to toggle. Passing an already armed tool disarms it.
         */
        protected void armTool(AbstractMapTool tool)
        {
            boolean arm = !tool.isArmed();

            if (this.mapCacheTool.isSelecting())
            {
                this.mapCacheTool.cancelSelection();
            }
            for (AbstractMapTool other : this.globeTools())
            {
                if (other != tool)
                {
                    other.setArmed(false);
                }
            }

            tool.setArmed(arm);
        }

        /**
         * Swaps the globe's elevation model for a flat one, and back.
         * <p>
         * The terrain tessellator drapes imagery over elevation tiles and hangs skirts off each tile edge to hide the
         * cracks between them. A tile that arrives with extreme or missing elevations gives that skirt an enormous
         * depth, and the imagery on it smears into long streaks across the view. Dropping the elevation model removes
         * that whole class of artifact. Nothing here needs relief: distances are geodesic either way, and the imagery
         * is what is being cached.
         * </p>
         *
         * @param flat true to use a flat globe, false to restore the elevation model the globe started with.
         */
        protected void setFlatTerrain(boolean flat)
        {
            Globe globe = this.getWwd().getModel().getGlobe();

            if (flat)
            {
                if (this.terrainElevationModel == null)
                {
                    this.terrainElevationModel = globe.getElevationModel();
                }
                globe.setElevationModel(new ZeroElevationModel());
            }
            else if (this.terrainElevationModel != null)
            {
                globe.setElevationModel(this.terrainElevationModel);
            }

            this.getWwd().redraw();
        }

        protected AbstractMapTool[] globeTools()
        {
            return new AbstractMapTool[] {this.distanceMeasureTool, this.pointMarkerTool, this.freehandDrawTool};
        }

        protected void disarmGlobeTools()
        {
            for (AbstractMapTool tool : this.globeTools())
            {
                tool.setArmed(false);
            }
        }

        protected void toggleMapSelection()
        {
            if (!this.mapCacheTool.isSelecting())
            {
                this.disarmGlobeTools();
            }
            this.mapCacheTool.toggleSelection();
        }

        protected void clearMapItems()
        {
            this.disarmGlobeTools();
            this.overlayManager.removeOverlays(null);
            this.pointMarkerTool.clearPoints();
            this.freehandDrawTool.clearDrawings();
        }

        protected void updateButtonLabels()
        {
            if (this.mapButton == null)
            {
                return;
            }

            if (this.mapCacheTool.isSelecting())
            {
                this.mapButton.setText("Cancel Map Selection");
                this.mapButton.setToolTipText("Cancel interactive area selection on the globe");
            }
            else
            {
                this.mapButton.setText("Cache from Map");
                this.mapButton.setToolTipText(
                    "Press, then drag on the globe; selected coordinates open in the cache dialog");
            }

            this.measureButton.setText(this.distanceMeasureTool.isArmed()
                ? "Stop Measuring" : "Measure Distance (" + this.distanceMeasureTool.getMeasurementCount() + ")");
            this.pointButton.setText(this.pointMarkerTool.isArmed()
                ? "Stop Adding Points" : "Add Point (" + this.pointMarkerTool.getPointCount() + ")");
            this.drawButton.setText(this.freehandDrawTool.isArmed()
                ? "Stop Drawing" : "Draw Border (" + this.freehandDrawTool.getDrawingCount() + ")");
            this.clearButton.setEnabled(this.overlayManager.getOverlayCount() > 0);
        }

        protected void openCoordinateDialog()
        {
            if (this.mapCacheTool.isSelecting())
            {
                this.mapCacheTool.cancelSelection();
            }
            this.disarmGlobeTools();

            if (!this.cacheDialog.isVisible())
            {
                this.cacheDialog.setLocationRelativeTo(this);
            }
            this.cacheDialog.setVisible(true);
            this.cacheDialog.toFront();
        }

        /**
         * Opens the shared cache dialog and fills coordinates from a map-selected sector.
         *
         * @param sector sector selected on the globe
         */
        protected void openCacheDialogWithSector(Sector sector)
        {
            this.cacheDialog.openWithSector(sector);
        }
    }

    public static void main(String[] args)
    {
        // Same window title as ApplicationTemplate so this is the default World Wind Application.
        ApplicationTemplate.start("WorldWind Application", AppFrame.class);
    }
}
