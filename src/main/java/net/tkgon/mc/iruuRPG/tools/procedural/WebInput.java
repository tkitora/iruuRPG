package net.tkgon.mc.iruuRPG.tools.procedural;

import java.util.List;

/** What a web is made from: the points chosen (block positions of air in the world) and the terrain round them. */
record WebInput(List<int[]> points, WebTerrain terrain) {
}
