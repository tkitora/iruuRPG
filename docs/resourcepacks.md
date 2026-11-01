# リソースパック (pack/)

サーバーが配るリソースパックは**3つ、別々**:

| パック | ファイル | 中身 | 作るもの |
|---|---|---|---|
| サーバー専用 | `server.zip` | `grass_block` の6面すべてを草(上面の草のテクスチャ、草の色つき)にする変更と、マインクラフトのゲーム音楽を消す `sounds.json`(iruuRPG のBGMは鳴る。レコードの音は残る) | jar に入っている(`src/main/resourcepack/server/`)。起動時に `plugins/iruuRPG/resourcepacks/` へ置く |
| モデル専用 | `iruumodel.zip` | `/iruumodel build` が作る、敵モデルのモデルとテクスチャ(docs/models.md) | モデル機能 |
| アイテムの見た目 | `iruuitem.zip` | アイテムが「基盤のアイテム(剣・弓・棒・皮の防具など)」の上に、別のバニラのアイテムの見た目を重ねるための、custom model data の割り当て(下の「アイテムの見た目」) | アイテム機能(`itempack/`) |
| 地面の印 | `iruumark.zip` | 攻撃の範囲の印のフォント `iruurpg:marks`(円・扇・四角の形。中身と外周の組。私用領域 U+E000〜。標準のフォントは変えない)、強攻撃のアイコンのフォント `iruurpg:hud`、画面効果のシェーダー(`screen-effects.enabled` が true のとき。docs/screen-effects.md) | 起動時にプラグインが描いて作る(`markpack/`、`combat/GroundMarks`) |

## 配り方
- `config.yml` の `resourcepack:`。`enabled: true` で、入ってきたプレイヤーに送る(1秒後)。パックごとに `enabled` / `required`。ファイルが無いパックは送らない。
- 場所は `base-url`(zip を配る Web サーバー。`plugins/iruuRPG/resourcepacks/` を配ればよい)。Web サーバーが無いときは、`host.enabled: true` でプラグイン内蔵の小さな配信を使える(`host.port` をプレイヤーに開け、`host.public-host` に公開アドレスを書く。GET だけ、`packs` の zip だけ)。
- **4つのパック(`server` / `models` / `items` / `marks`)は、config.yml に書いていなくても送る**(ファイルがあれば)。デプロイは config.yml に新しい最上位の節しか足さないので、あとから `resourcepack.packs` に足したパックは、既存のファイルには入らない。config に `packs.<名前>:` があれば、そちらが勝つ(`enabled: false` で止める、`file`、`required`)。
- 地面の印のパック(`marks`)は、作り直して中身が変わったとき (プラグインの更新)、ログイン中のプレイヤーにも送り直す。
- アイテムの見た目のパック(`items`)は、yml が変わって作り直されたとき、**ログイン中のプレイヤーにも自動で送り直す**(あとから入る人は、入るときに送る)。クライアントでは古いパックと置き換わる。
- パックごとに固定のIDで送るので、パックを作り直すと、クライアントでは古いのが置き換わる(増えない)。URL の `?h=` は、古いキャッシュを避けるためのハッシュ。
- `/iruurpg pack` … 場所と、各パックの大きさ・sha1。`/iruurpg pack send` … 自分に送り直す。

## サーバー専用パックに変更を足すには
`src/main/resourcepack/server/` に、vanilla と同じパスでファイルを置く(`pack_format` は 22 = 1.20.3〜1.20.4)。ビルドで再現できる zip になる(中身が変わらなければ hash も変わらない)。

## 消すには
- 配信をやめる: `resourcepack.enabled: false`。
- サーバー専用パックをやめる: `resourcepack.packs.server.enabled: false`(`resourcepacks/server.zip` と、jar の `src/main/resourcepack/`、`build.gradle` の `serverResourcePack` で全部)。
- コードは `pack/` の2ファイルと、`IruuRPG.java` の `resourcePackService`、`IruuRPGCommand` の `pack` だけ。

## アイテムの見た目 (`iruuitem.zip`、`itempack/`)
アイテムは、種類ごとの**基盤のアイテム**(物理近接 = 剣・斧 / 物理遠距離 = 弓・クロスボウ / 魔法近接 = くわ / 魔法遠距離 = ブレイズロッド / 設置 = グローストーン・シーランタン / 防具 = 皮の防具 / 素材 = 棒)の上に、**別のバニラのアイテムの見た目**を重ねる。新しい絵は描かない。
- パックは、アイテムごとの `(id, 基盤, 見た目)` から作る(`ItemPackBuilder`)。見た目が基盤と同じなら何もしない(custom model data も付けない)。
- 基盤のアイテムのモデルは、**バニラのファイルをそのまま**(`src/main/resources/itempack/base/<アイテム>.json`、1.20.4 のもの)読み、その override の**あとに**、見た目ごとの `custom_model_data` の override を足す(ゲームは「合う最後の override」を使うので、弓の引く動作や防具の模様のバニラの override は残る)。
- 見た目のモデルは、見た目のバニラのアイテムを指すだけ: 剣・斧・くわ・ブレイズロッドの基盤で、見た目が平らな絵のアイテムなら `item/handheld` + その絵(剣と同じ持ち方)、弓・クロスボウなら基盤のモデルを親にして絵だけ替える、ほかは見た目のアイテムのモデルをまるごと(ブロックは立体のまま)。名前空間は `iruurpg`。
- custom model data の番号は、`plugins/iruuRPG/resourcepacks/item-cmd-index.yml`(`id: 番号`)に残す(`ItemCmdIndex`)。**番号は変えない・使い回さない**(プレイヤーの手元のアイテムが、その番号を持っているため)。最初の番号は 1000000(モデル機能の部品は 8100000 以上で、別のアイテム `PAPER` に付くので、ぶつからない)。
- 同じ入力なら同じバイトの zip になる(hash が変わるのは、見た目が変わったときだけ)。
- 注意: 基盤のモデルを置き換えるので、**基盤のバニラの override は、1.20.4 のファイルのもの**だけになる(ほかのパックが同じ基盤のモデルを変えていると、後に読まれた方が勝つ)。
- 配る: `config.yml` の `resourcepack.packs.items`(ファイルが無い間は送られない)。
- **アイテム自身の絵**: `texture:` にバニラのアイテム名でない名前(`a-z0-9_`)を書き、**`plugins/iruuRPG/item-textures/<名前>.png`**(16x16 の PNG。yml のリポジトリの `item-textures/` が、そのまま入る)を置くと、その絵が見た目になる(パックの `assets/iruurpg/textures/item/custom/`。持ち方は基盤のまま)。PNG が無く、バニラのアイテムでもない名前は、警告を出して基盤の見た目のまま。
- **ゲームが色を付けるバニラのアイテム**(シダ・草・つる・葉など): 絵は灰色で、基盤の上では色が付かず白く出る。プラグインが、色付きの絵を `itempack/tinted/<名前>.png` に持っていれば、それをパックに入れる(今は `fern`)。
