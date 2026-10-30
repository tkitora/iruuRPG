# リソースパック (pack/)

サーバーが配るリソースパックは**2つ、別々**:

| パック | ファイル | 中身 | 作るもの |
|---|---|---|---|
| サーバー専用 | `server.zip` | `grass_block` の6面すべてを草(上面の草のテクスチャ、草の色つき)にする変更と、マインクラフトのゲーム音楽を消す `sounds.json`(iruuRPG のBGMは鳴る。レコードの音は残る) | jar に入っている(`src/main/resourcepack/server/`)。起動時に `plugins/iruuRPG/resourcepacks/` へ置く |
| モデル専用 | `iruumodel.zip` | `/iruumodel build` が作る、敵モデルのモデルとテクスチャ(docs/models.md) | モデル機能 |

## 配り方
- `config.yml` の `resourcepack:`。`enabled: true` で、入ってきたプレイヤーに送る(1秒後)。パックごとに `enabled` / `required`。ファイルが無いパックは送らない。
- 場所は `base-url`(zip を配る Web サーバー。`plugins/iruuRPG/resourcepacks/` を配ればよい)。Web サーバーが無いときは、`host.enabled: true` でプラグイン内蔵の小さな配信を使える(`host.port` をプレイヤーに開け、`host.public-host` に公開アドレスを書く。GET だけ、`packs` の zip だけ)。
- パックごとに固定のIDで送るので、パックを作り直すと、クライアントでは古いのが置き換わる(増えない)。URL の `?h=` は、古いキャッシュを避けるためのハッシュ。
- `/iruurpg pack` … 場所と、各パックの大きさ・sha1。`/iruurpg pack send` … 自分に送り直す。

## サーバー専用パックに変更を足すには
`src/main/resourcepack/server/` に、vanilla と同じパスでファイルを置く(`pack_format` は 22 = 1.20.3〜1.20.4)。ビルドで再現できる zip になる(中身が変わらなければ hash も変わらない)。

## 消すには
- 配信をやめる: `resourcepack.enabled: false`。
- サーバー専用パックをやめる: `resourcepack.packs.server.enabled: false`(`resourcepacks/server.zip` と、jar の `src/main/resourcepack/`、`build.gradle` の `serverResourcePack` で全部)。
- コードは `pack/` の2ファイルと、`IruuRPG.java` の `resourcePackService`、`IruuRPGCommand` の `pack` だけ。
