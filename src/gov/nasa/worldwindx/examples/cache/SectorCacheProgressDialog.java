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

import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.retrieve.BulkRetrievalThread;
import gov.nasa.worldwind.util.WWMath;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Simple progress window for one or more bulk cache download threads.
 *
 * @author Cursor Agent
 */
public class SectorCacheProgressDialog extends JDialog
{
    protected final JPanel monitorPanel = new JPanel();
    protected final JLabel sectorLabel = new JLabel("-");

    public SectorCacheProgressDialog(Frame owner)
    {
        super(owner, "Cache Download Progress", false);
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                closeDialog();
            }
        });

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel sectorPanel = new JPanel(new BorderLayout());
        sectorPanel.setBorder(new TitledBorder("Sector"));
        sectorPanel.add(this.sectorLabel, BorderLayout.CENTER);
        root.add(sectorPanel, BorderLayout.NORTH);

        this.monitorPanel.setLayout(new BoxLayout(this.monitorPanel, BoxLayout.Y_AXIS));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(this.monitorPanel, BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(wrapper);
        scrollPane.setPreferredSize(new Dimension(420, 220));
        scrollPane.setBorder(new TitledBorder("Downloads"));
        root.add(scrollPane, BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                closeDialog();
            }
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(closeButton);
        root.add(south, BorderLayout.SOUTH);

        this.getContentPane().add(root);
        this.pack();
        this.setLocationRelativeTo(owner);
    }

    public void showDownloads(Sector sector, List<BulkRetrievalThread> threads)
    {
        this.sectorLabel.setText(SectorCacheController.makeSectorDescription(sector));
        this.monitorPanel.removeAll();
        if (threads != null)
        {
            for (BulkRetrievalThread thread : threads)
            {
                if (thread != null)
                {
                    this.monitorPanel.add(new DownloadMonitorPanel(thread));
                }
            }
        }
        this.monitorPanel.revalidate();
        this.monitorPanel.repaint();
        this.setVisible(true);
        this.toFront();
    }

    public boolean hasActiveDownloads()
    {
        for (Component component : this.monitorPanel.getComponents())
        {
            if (component instanceof DownloadMonitorPanel
                && ((DownloadMonitorPanel) component).thread.isAlive())
            {
                return true;
            }
        }
        return false;
    }

    public void cancelActiveDownloads()
    {
        for (Component component : this.monitorPanel.getComponents())
        {
            if (component instanceof DownloadMonitorPanel)
            {
                DownloadMonitorPanel panel = (DownloadMonitorPanel) component;
                if (panel.thread.isAlive())
                {
                    panel.cancel();
                }
            }
        }
    }

    protected void closeDialog()
    {
        if (this.hasActiveDownloads())
        {
            int choice = JOptionPane.showConfirmDialog(this, "Cancel all active downloads?",
                "Active downloads", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.OK_OPTION)
            {
                return;
            }
            this.cancelActiveDownloads();
        }
        this.setVisible(false);
    }

    protected static class DownloadMonitorPanel extends JPanel
    {
        protected final BulkRetrievalThread thread;
        protected final JLabel descriptionLabel;
        protected final JProgressBar progressBar;
        protected final JButton cancelButton;
        protected final Timer updateTimer;

        DownloadMonitorPanel(BulkRetrievalThread thread)
        {
            this.thread = thread;
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            this.setBorder(new EmptyBorder(4, 4, 4, 4));

            this.descriptionLabel = new JLabel(thread.getRetrievable().getName());
            this.progressBar = new JProgressBar(0, 100);
            this.cancelButton = new JButton("Cancel");
            this.cancelButton.setBackground(Color.RED);
            this.cancelButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (DownloadMonitorPanel.this.thread.isAlive())
                    {
                        cancel();
                    }
                    else
                    {
                        Container parent = getParent();
                        if (parent != null)
                        {
                            parent.remove(DownloadMonitorPanel.this);
                            parent.revalidate();
                            parent.repaint();
                        }
                    }
                }
            });

            JPanel progressRow = new JPanel();
            progressRow.setLayout(new BoxLayout(progressRow, BoxLayout.X_AXIS));
            progressRow.add(this.progressBar);
            progressRow.add(Box.createHorizontalStrut(8));
            progressRow.add(this.cancelButton);

            this.add(this.descriptionLabel);
            this.add(progressRow);

            this.updateTimer = new Timer(1000, new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    updateStatus();
                }
            });
            this.updateTimer.start();
        }

        void cancel()
        {
            this.thread.interrupt();
            this.cancelButton.setText("Remove");
            this.cancelButton.setBackground(Color.ORANGE);
            this.updateTimer.stop();
        }

        protected void updateStatus()
        {
            long current = this.thread.getProgress().getCurrentSize();
            long total = this.thread.getProgress().getTotalSize();
            String name = this.thread.getRetrievable().getName();
            if (name.length() > 28)
            {
                name = name.substring(0, 25) + "...";
            }
            this.descriptionLabel.setText(name + " (" + SectorCacheController.makeSizeDescription(current)
                + " / " + SectorCacheController.makeSizeDescription(total) + ")");

            int percent = 0;
            if (this.thread.getProgress().getTotalCount() > 0)
            {
                percent = (int) WWMath.clamp(
                    (this.thread.getProgress().getCurrentCount() * 100.0)
                        / this.thread.getProgress().getTotalCount(), 0, 100);
            }
            this.progressBar.setValue(percent);

            if (!this.thread.isAlive())
            {
                this.cancelButton.setText("Remove");
                this.cancelButton.setBackground(Color.GREEN);
                this.updateTimer.stop();
            }
        }
    }
}
