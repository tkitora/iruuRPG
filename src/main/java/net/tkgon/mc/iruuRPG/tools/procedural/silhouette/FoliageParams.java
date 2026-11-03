package net.tkgon.mc.iruuRPG.tools.procedural.silhouette;

/**
 * 葉塊の量子化パラメータ。
 * blobs/spread  : 1アンカーを何個の小塊(メタボール)で構成するか、どれだけ散らすか
 * threshold     : 場の閾値(大きいほど痩せる)
 * noiseAmp/Scale: 輪郭を崩す値ノイズ
 * erode         : 表面ブロックを間引く確率(ボコボコ感)
 * protrude      : 表面から1ブロック突き出す確率(葉の「トゲ」)
 * drip*         : 下面から垂れる房
 * dripOuter     : 1 に近いほど外周ばかりに垂れる(カーテン状)
 */
public record FoliageParams(int blobs, double spread, double threshold, double noiseAmp, double noiseScale,
                            double erode, double protrude,
                            double dripChance, int dripMin, int dripMax, double dripOuter) {
}
