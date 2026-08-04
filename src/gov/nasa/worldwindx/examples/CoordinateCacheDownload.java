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
import gov.nasa.worldwind.util.WWUtil;
import gov.nasa.worldwindx.examples.cache.CoordinateCacheDialog;
import gov.nasa.worldwindx.examples.cache.DistanceMeasureTool;
import gov.nasa.worldwindx.examples.cache.MapSectorCacheTool;
import gov.nasa.worldwindx.examples.cache.SectorCacheController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Demonstrates sector cache download and Google Maps-style distance measuring.
 * <p>
 * Lower-left buttons provide:
 * </p>
 * <ul>
 * <li>Manual coordinate entry through {@link CoordinateCacheDialog}</li>
 * <li>Interactive map selection through {@link MapSectorCacheTool}</li>
 * <li>Distance measuring through {@link DistanceMeasureTool}</li>
 * </ul>
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
        protected DistanceMeasureTool distanceMeasureTool;
        protected JButton coordinateButton;
        protected JButton mapButton;
        protected JButton measureButton;
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
                    updateMapButtonLabel();
                }
            });
            this.distanceMeasureTool = new DistanceMeasureTool(this.getWwd());
            this.distanceMeasureTool.setArmedStateListener(new Runnable()
            {
                public void run()
                {
                    updateMeasureButtonLabel();
                }
            });
            this.installBottomLeftCacheButtons();

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
            this.coordinateButton = new JButton("Cache by Coordinates");
            this.coordinateButton.setToolTipText("Enter coordinates and download the area into the local cache");
            this.coordinateButton.setFocusable(false);
            this.coordinateButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    openCoordinateDialog();
                }
            });

            this.mapButton = new JButton("Cache from Map");
            this.mapButton.setToolTipText("Press, then drag on the globe to select an area to download");
            this.mapButton.setFocusable(false);
            this.mapButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    toggleMapSelection();
                }
            });

            this.measureButton = new JButton("Measure Distance");
            this.measureButton.setToolTipText("Press, then drag on the globe to measure distance from the start point");
            this.measureButton.setFocusable(false);
            this.measureButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    toggleDistanceMeasure();
                }
            });

            this.buttonStack = new JPanel();
            this.buttonStack.setOpaque(false);
            this.buttonStack.setLayout(new BoxLayout(this.buttonStack, BoxLayout.Y_AXIS));
            this.coordinateButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            this.mapButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            this.measureButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            this.buttonStack.add(this.coordinateButton);
            this.buttonStack.add(Box.createVerticalStrut(6));
            this.buttonStack.add(this.mapButton);
            this.buttonStack.add(Box.createVerticalStrut(6));
            this.buttonStack.add(this.measureButton);

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

        protected void toggleMapSelection()
        {
            if (!this.mapCacheTool.isSelecting() && this.distanceMeasureTool.isArmed())
            {
                this.distanceMeasureTool.setArmed(false);
            }
            this.mapCacheTool.toggleSelection();
        }

        protected void toggleDistanceMeasure()
        {
            if (!this.distanceMeasureTool.isArmed() && this.mapCacheTool.isSelecting())
            {
                this.mapCacheTool.cancelSelection();
            }
            this.distanceMeasureTool.toggleArmed();
        }

        protected void updateMapButtonLabel()
        {
            if (this.mapButton == null || this.mapCacheTool == null)
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
                this.mapButton.setToolTipText("Press, then drag on the globe to select an area to download");
            }
        }

        protected void updateMeasureButtonLabel()
        {
            if (this.measureButton == null || this.distanceMeasureTool == null)
            {
                return;
            }

            if (this.distanceMeasureTool.isArmed())
            {
                this.measureButton.setText("Cancel Measure");
                this.measureButton.setToolTipText("Cancel distance measuring");
            }
            else
            {
                this.measureButton.setText("Measure Distance");
                this.measureButton.setToolTipText(
                    "Press, then drag on the globe to measure distance from the start point");
            }
        }

        protected void openCoordinateDialog()
        {
            if (this.mapCacheTool.isSelecting())
            {
                this.mapCacheTool.cancelSelection();
            }
            if (this.distanceMeasureTool.isArmed())
            {
                this.distanceMeasureTool.setArmed(false);
            }

            if (!this.cacheDialog.isVisible())
            {
                this.cacheDialog.setLocationRelativeTo(this);
            }
            this.cacheDialog.setVisible(true);
            this.cacheDialog.toFront();
        }
    }

    public static void main(String[] args)
    {
        ApplicationTemplate.start("WorldWind Coordinate Cache Download", AppFrame.class);
    }
}
