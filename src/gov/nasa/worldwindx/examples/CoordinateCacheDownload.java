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
import gov.nasa.worldwindx.examples.cache.SectorCacheController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Demonstrates manual coordinate entry for bulk-caching a geographic sector.
 * <p>
 * A button in the lower-left of the WorldWindow opens a dialog where the user types min/max latitude and longitude.
 * The entered sector is previewed on the globe and downloaded into the WorldWind cache through
 * {@link SectorCacheController}. Interactive map selection can later reuse the same controller.
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
        protected JButton cacheButton;

        public AppFrame()
        {
            this.cacheController = new SectorCacheController(this.getWwd());
            this.cacheDialog = new CoordinateCacheDialog(this, this.cacheController);
            this.installBottomLeftCacheButton();

            Dimension size = new Dimension(1200, 800);
            this.setPreferredSize(size);
            this.pack();
            WWUtil.alignComponent(null, this, AVKey.CENTER);
        }

        /**
         * Places a non-blocking glass-pane button over the lower-left corner of the WorldWindow.
         * Clicks outside the button pass through to the globe and other UI.
         */
        protected void installBottomLeftCacheButton()
        {
            this.cacheButton = new JButton("Cache by Coordinates");
            this.cacheButton.setToolTipText("Enter coordinates and download the area into the local cache");
            this.cacheButton.setFocusable(false);
            this.cacheButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    openCacheDialog();
                }
            });

            final JPanel glass = new JPanel(null)
            {
                @Override
                public boolean contains(int x, int y)
                {
                    if (cacheButton == null || !cacheButton.isShowing())
                    {
                        return false;
                    }
                    Point p = SwingUtilities.convertPoint(this, new Point(x, y), cacheButton);
                    return cacheButton.contains(p);
                }
            };
            glass.setOpaque(false);
            glass.add(this.cacheButton);

            this.setGlassPane(glass);
            glass.setVisible(true);

            final Component worldWindow = (Component) this.getWwd();
            ComponentAdapter repositionListener = new ComponentAdapter()
            {
                public void componentResized(ComponentEvent e)
                {
                    repositionCacheButton(glass, worldWindow);
                }

                public void componentMoved(ComponentEvent e)
                {
                    repositionCacheButton(glass, worldWindow);
                }

                public void componentShown(ComponentEvent e)
                {
                    repositionCacheButton(glass, worldWindow);
                }
            };
            worldWindow.addComponentListener(repositionListener);
            this.getWwjPanel().addComponentListener(repositionListener);
            this.addComponentListener(repositionListener);

            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    repositionCacheButton(glass, worldWindow);
                }
            });
        }

        protected void repositionCacheButton(JPanel glass, Component worldWindow)
        {
            if (glass == null || this.cacheButton == null || worldWindow == null || !worldWindow.isShowing())
            {
                return;
            }

            Dimension buttonSize = this.cacheButton.getPreferredSize();
            Point worldWindowOrigin = SwingUtilities.convertPoint(worldWindow.getParent(),
                worldWindow.getLocation(), glass);

            // Keep the button in the WorldWindow's lower-left corner, above the on-canvas view controls.
            int margin = 12;
            int liftAboveViewControls = 96;
            int x = worldWindowOrigin.x + margin;
            int y = worldWindowOrigin.y + worldWindow.getHeight() - buttonSize.height - margin - liftAboveViewControls;

            y = Math.max(worldWindowOrigin.y + margin, y);
            this.cacheButton.setBounds(x, y, buttonSize.width, buttonSize.height);
            glass.revalidate();
            glass.repaint();
        }

        protected void openCacheDialog()
        {
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
