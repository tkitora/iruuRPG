# サーバー専用リソースパック (src/main/resourcepack/server)

ビルドで `server.zip` になり、jar の `resourcepacks/server.zip` に入る。プラグインが起動時に `plugins/iruuRPG/resourcepacks/server.zip` へ置き、入ってきたプレイヤーに送る(`resourcepack:` の設定、docs/resourcepacks.md)。

入っている変更は1つだけ: `assets/minecraft/models/block/grass_block.json` — 草ブロック(grass_block)の6面すべてを、上面の草のテクスチャ(草の色つき)にする。ほかのブロック・テクスチャは変えない。変更を足すときは、この server/ に vanilla と同じパスで置く。

モデル機能(`/iruumodel`)のパックは、これとは別(`iruumodel.zip`、モデル機能が作る)。
