package com.cedarsoftware.ncube.proximity;


import com.cedarsoftware.ncube.CellInfo;

/**
 * This class is used to represent a 2D point.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class Point2D implements Comparable<Point2D>, Distance<Point2D>
{
	private final double x;
	private final double y;

	public Point2D(double x, double y)
	{
		this.x = x;
		this.y = y;
	}

	public boolean equals(Object obj)
	{
		if (!(obj instanceof Point2D))
		{
			return false;
		}

		Point2D that = (Point2D) obj;
		return x == that.x && y == that.y;
	}

	public double distance(Point2D that)
	{
		double dx = that.x - x;
		double dy = that.y - y;
		return Math.sqrt(dx * dx + dy * dy);
	}

	public String toString()
	{
        return String.format("%s, %s", CellInfo.formatForEditing(x), CellInfo.formatForEditing(y));
	}

	public int compareTo(Point2D that)
	{
		if (x < that.x)
		{
			return -1;
		}
		if (x > that.x)
		{
			return 1;
		}
		if (y < that.y)
		{
			return -1;
		}
		if (y > that.y)
		{
			return 1;
		}
		return 0;
	}

    public double getX() { return x; }
    public double getY() { return y; }
}
