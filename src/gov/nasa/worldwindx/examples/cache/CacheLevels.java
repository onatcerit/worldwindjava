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

import gov.nasa.worldwind.util.Level;
import gov.nasa.worldwind.util.LevelSet;

/**
 * Translates between WorldWind internal level numbers and the zoom folder names written to the file cache.
 * <p>
 * WorldWind writes tiles to <code>&lt;cache root&gt;/&lt;DataCacheName&gt;/&lt;level name&gt;/&lt;row&gt;/...</code>,
 * for example <code>C:\ProgramData\WorldWindData\Earth\Bing\9\...</code> on Windows. {@link LevelSet} names the levels
 * <code>0, 1, 2, ...</code> after skipping the layer's empty levels, so the folder name and the internal level number
 * are not the same value. The cache tools address levels by folder name, because that is what the user sees on disk.
 * </p>
 *
 * @author Cursor Agent
 */
public class CacheLevels
{
    /** Returned when a level has no cache folder, that is when the level is empty. */
    public static final int NO_FOLDER = -1;

    /**
     * Returns the cache folder name of a level, as an integer.
     *
     * @param level the level to inspect. May be null.
     *
     * @return the folder name written under the layer's cache directory, or {@link #NO_FOLDER} if the level holds no
     *         data.
     */
    public static int folderNameOf(Level level)
    {
        if (level == null || level.isEmpty())
        {
            return NO_FOLDER;
        }

        String name = level.getLevelName();
        if (name != null && name.length() > 0)
        {
            try
            {
                return Integer.parseInt(name.trim());
            }
            catch (NumberFormatException ignore)
            {
                // Level names are normally plain integers; fall back to the internal number when they are not.
            }
        }

        return level.getLevelNumber();
    }

    /**
     * Returns the cache folder name of a level within a level set.
     *
     * @param levels      the level set to inspect.
     * @param levelNumber the internal level number.
     *
     * @return the folder name, or {@link #NO_FOLDER} if the level is empty or out of bounds.
     */
    public static int folderNameOf(LevelSet levels, int levelNumber)
    {
        if (levels == null || levelNumber < 0 || levelNumber > levels.getLastLevel().getLevelNumber())
        {
            return NO_FOLDER;
        }
        if (levels.isLevelEmpty(levelNumber))
        {
            return NO_FOLDER;
        }

        return folderNameOf(levels.getLevel(levelNumber));
    }

    /**
     * Indicates whether a level's cache folder falls inside the requested folder range.
     *
     * @param levels      the level set to inspect.
     * @param levelNumber the internal level number.
     * @param minFolder   lowest folder name to accept, inclusive.
     * @param maxFolder   highest folder name to accept, inclusive.
     *
     * @return true if the level holds data and its folder name is within the range.
     */
    public static boolean isFolderInRange(LevelSet levels, int levelNumber, int minFolder, int maxFolder)
    {
        int folder = folderNameOf(levels, levelNumber);
        return folder != NO_FOLDER && folder >= minFolder && folder <= maxFolder;
    }

    /**
     * Returns the lowest folder name the level set can supply.
     *
     * @param levels the level set to inspect.
     *
     * @return the lowest available folder name, or {@link #NO_FOLDER} when the level set holds no data.
     */
    public static int minAvailableFolder(LevelSet levels)
    {
        if (levels == null)
        {
            return NO_FOLDER;
        }

        for (int i = 0; i <= levels.getLastLevel().getLevelNumber(); i++)
        {
            int folder = folderNameOf(levels, i);
            if (folder != NO_FOLDER)
            {
                return folder;
            }
        }

        return NO_FOLDER;
    }

    /**
     * Returns the highest folder name the level set can supply.
     *
     * @param levels the level set to inspect.
     *
     * @return the highest available folder name, or {@link #NO_FOLDER} when the level set holds no data.
     */
    public static int maxAvailableFolder(LevelSet levels)
    {
        if (levels == null)
        {
            return NO_FOLDER;
        }

        int best = NO_FOLDER;
        for (int i = 0; i <= levels.getLastLevel().getLevelNumber(); i++)
        {
            int folder = folderNameOf(levels, i);
            if (folder != NO_FOLDER && folder > best)
            {
                best = folder;
            }
        }

        return best;
    }

    /**
     * Returns the internal level number holding the highest folder name that is still at or below
     * <code>maxFolder</code>. This is the finest level a download of the requested range will reach.
     *
     * @param levels    the level set to inspect.
     * @param maxFolder highest folder name the caller asked for.
     *
     * @return the internal level number, or -1 when no level qualifies.
     */
    public static int highestLevelNumberAtOrBelow(LevelSet levels, int maxFolder)
    {
        if (levels == null)
        {
            return -1;
        }

        int best = -1;
        int bestFolder = NO_FOLDER;
        for (int i = 0; i <= levels.getLastLevel().getLevelNumber(); i++)
        {
            int folder = folderNameOf(levels, i);
            if (folder != NO_FOLDER && folder <= maxFolder && folder > bestFolder)
            {
                bestFolder = folder;
                best = i;
            }
        }

        return best;
    }

    /**
     * Returns the folder range a download will actually write, which is the requested range clipped to what the level
     * set can supply.
     *
     * @param levels    the level set to inspect.
     * @param minFolder lowest folder name the caller asked for.
     * @param maxFolder highest folder name the caller asked for.
     *
     * @return a two element array holding the effective minimum and maximum folder names, or null when the requested
     *         range and the available range do not overlap.
     */
    public static int[] effectiveFolderRange(LevelSet levels, int minFolder, int maxFolder)
    {
        int available = minAvailableFolder(levels);
        int availableMax = maxAvailableFolder(levels);
        if (available == NO_FOLDER || availableMax == NO_FOLDER)
        {
            return null;
        }

        int min = Math.max(minFolder, available);
        int max = Math.min(maxFolder, availableMax);
        if (min > max)
        {
            return null;
        }

        return new int[] {min, max};
    }

    /**
     * Formats a folder range for display, for example <code>5-9</code> or <code>7</code>.
     *
     * @param range a two element array as returned by {@link #effectiveFolderRange(LevelSet, int, int)}. May be null.
     *
     * @return a human readable range, or <code>none</code> when the range is null.
     */
    public static String describeFolderRange(int[] range)
    {
        if (range == null)
        {
            return "none";
        }
        if (range[0] == range[1])
        {
            return Integer.toString(range[0]);
        }

        return range[0] + "-" + range[1];
    }

    protected CacheLevels()
    {
        // Utility class; not instantiated.
    }
}
