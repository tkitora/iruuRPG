# サーバー専用リソースパック (src/main/resourcepack/server)

ビルドで `server.zip` になり、jar の `resourcepacks/server.zip` に入る。プラグインが起動時に `plugins/iruuRPG/resourcepacks/server.zip` へ置き、入ってきたプレイヤーに送る(`resourcepack:` の設定、docs/resourcepacks.md)。

入っている変更は2つ:
- `assets/minecraft/models/block/grass_block.json` — 草ブロック(grass_block)の6面すべてを、上面の草のテクスチャ(草の色つき)にする。ほかのブロック・テクスチャは変えない。
- `assets/minecraft/sounds.json` — マインクラフトのゲーム音楽(`music.game`、`music.creative`、`music.menu`、水中・ネザー・エンド・各バイオームの `music.*`)を `replace: true` で空にして、鳴らなくする。iruuRPG のBGMは別の仕組み(ノートブロックの音を `MUSIC` 区分で送る)なので鳴る。レコード(ジュークボックス)の音は変えない。変更を足すときは、この server/ に vanilla と同じパスで置く。

モデル機能(`/iruumodel`)のパックは、これとは別(`iruumodel.zip`、モデル機能が作る)。
