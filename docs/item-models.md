# アイテムの見た目を Blockbench のモデルにする (`model:`、t154)

アイテムの yml に `model:` を書くと、Blockbench で作ったモデル (`.bbmodel`) が、そのアイテムの見た目になる。見えるのは次のすべて:
- 手に持ったとき (自分の一人称、ほかの人から見た三人称)
- インベントリのスロット (モデル全体が、スロットの四角に収まる)
- 地面に落ちたとき、額縁、頭にかぶったとき
- **敵の装備**: 敵の yml の `equipment:` に、アイテムの id を書く (`main_hand: test_omni_sword`)。敵は同じ見た目で持つ。モデルを着た敵 (docs/models.md) は体を隠すので、持ち物は見えない。
- **設置の武器**: モデルのある設置の武器は、置いたもの (見えないアーマースタンド) が、属性のブロックの代わりに武器のモデルをかぶる。

## 書き方
```yaml
items:
  test_omni_sword:
    material: IRON_SWORD
    model: sample_sword          # models/sample_sword.bbmodel。持ち方は武器の種類から決まる

  test_omni_wand:
    material: BLAZE_ROD
    model:
      id: sample_staff
      hold: staff                # 持ち方 (下の表)
      scale: 1.2                 # 手・地面・額縁・頭での大きさ (スロットは自動で収める)
      hand:                      # 手での調整
        rotation: [0, 0, 10]     #   持つ所を中心に回す (度: x, y, z)
        translation: [0, -2, 0]  #   モデルの向きで動かす (16 = 1 ブロック)。y を - にすると、モデルの上の方を持つ
        scale: 1.0
      gui:                       # スロットでの調整 (同じ形)
        rotation: [0, 20, 0]
      display:                   # 場所ごとに、ゲームの値をそのまま書く (ほかの設定より強い)
        ground: {rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4]}
```
- モデルの id は、`models/` のファイル名から `.bbmodel` を除いたもの。半角の小文字・数字・`_` だけ。
- `model:` は `texture:` より先に使う。モデルが無い・読めないときは、`texture:` の見た目 (無ければ基盤のアイテムの見た目) のままで、サーバーのログに理由が出る。
- `display:` の場所の名前: `thirdperson_righthand`、`thirdperson_lefthand`、`firstperson_righthand`、`firstperson_lefthand`、`gui`、`ground`、`fixed` (額縁)、`head`。値は Blockbench の Display と同じ (rotation は度、translation は 1/16 ブロックで ±80 まで、scale は 4 まで)。左手を書かなければ、右手の左右反対になる。

## 持ち方 (`hold:`)
バニラのアイテムの持ち方をもとにしている。モデルは、バニラのアイテムの絵があるはずの所に置かれる。

| hold | 持ち方 | 既定になるアイテム |
|---|---|---|
| `sword` | 剣と同じ (斜め前に構える) | 近接 (MELEE) |
| `staff` | 剣と同じ向きで、モデルの下から 3 割の所を持つ | 遠距離 (RANGE)。基盤が弓・クロスボウなら `bow` |
| `rod` | 釣り竿と同じ | |
| `bow` | 弓と同じ (縦に構える)。モデルの真ん中を持つ | 遠距離で、基盤が弓・クロスボウ |
| `item` (= `lantern`) | 平らなアイテムの絵と同じ (ランタン・花のように、立てて持つ)。モデルを絵の四角に収める | 設置 (DEPLOY) と、そのほか |
| `block` | ブロックと同じ。モデルをブロックの大きさに収める | |
| `model` | Blockbench の Display の設定をそのまま使う (下) | Display の設定があるモデル |

`sword` / `staff` / `rod` / `bow` のモデルは:
- **立てて作る**。先が上 (+Y)、平らな面が前後 (±Z)。剣なら、刃の平らな面が前を向き、つばが左右 (±X) に広がる。 弓は、弓の木を -X 側、弦を +X 側にする (バニラの弓の絵と同じ向きになる)。
- **持つ所に、`grip` という名前のグループ (またはロケーター) を置く**。グループの原点 (pivot) を持つ。無ければ、モデルの縦の真ん中の線の、下からの割合 (sword 18 %、staff 30 %、rod 12 %、bow 50 %)。
- **大きさは、そのまま** (16 = 1 ブロック)。バニラの剣は、長さ約 22 (斜めの絵)。長さ 24 の剣なら、バニラの剣とほぼ同じ大きさ。

スロット・地面・額縁・頭では、どの持ち方でも、モデル全体を四角 (`block` はブロック) に収める (`sword` などは斜めに、`item` は少し横と上から見た向き、`block` はブロックと同じ向き)。

### Blockbench の Display をそのまま使う (`hold: model`)
Blockbench の **Java Block/Item** 形式で作り、Display モード (右上) で持ち方を決めて保存したモデルは、その設定を使う (`hold:` を書かなければ、これが既定)。このとき、モデルは作った位置のまま (Java Block/Item のグリッドは、ブロックの 0〜16)。Display で決めていない場所 (スロットなど) は、`item` と同じに作る。Generic Model には Display モードが無いので、上の持ち方を使う。

## Blockbench での作り方
- 形式: **Generic Model** (敵のモデルと同じ) か **Java Block/Item**。どちらも、テクスチャを**埋め込み**で保存する。
- 使えるのは**キューブだけ** (メッシュは飛ばす)。アニメーションは使わない。
- **回転**: ゲームのモデルは、1 つのキューブを **1 軸だけ、-45・-22.5・22.5・45 度**しか回せない。それ以外 (2 軸、30 度、回したグループの中のキューブ) は、いちばん近い回り方にして、ログに警告を出す。Java Block/Item 形式なら、Blockbench が最初からこの制限で作らせる。
- **大きさ**: ゲームのモデルは -16〜32 の範囲 (長さ 48) まで。それより長いモデルは、小さく作って、表示で大きくする (表示の拡大は 4 倍まで。剣の持ち方なら長さ約 200 まで)。
- テクスチャの名前は、パックのパスにするとき小文字と `_` にする (`Sword.png` → `sword`)。
- アップロード: ツールサイトの Items の「モデル (bbmodel)」欄、または Mobs の「モデル」欄から (`models/<id>.bbmodel` に保存)。サーバーは約 1 分で読み込み、アイテムのパックを作り直して、オンラインの人に送り直す。

## サンプル (`models/` に置いてある)
| モデル | アイテム | 持ち方 |
|---|---|---|
| `sample_sword` (長さ 25 の剣: 柄・つば・赤い石・刃) | `test_omni_sword` (近接) | `sword` |
| `sample_staff` (長さ 31 の杖: 木の柄・金の帯・4 本の爪・青い水晶。爪は 22.5 度、水晶は 45 度回している) | `test_omni_wand` (遠距離) | `staff` |
| `sample_lantern` (高さ 16 のランタン: 枠・ガラス (中が透ける)・炎・取っ手) | `test_omni_lantern` (設置) | `lantern` |

作るスクリプトは、ワークスペースの `design/item-bbmodel-samples/make_samples.py`。

## しくみ
- `itempack/BbItemModel`: .bbmodel を読む。キューブを、ゲームのモデルの座標 (Generic Model は x と z に 8 を足す) の要素にする。キューブ自身とグループの回転を合わせて、いちばん近い許される回転にする (箱は、真ん中の行き先に置いて、真ん中を中心に回す)。テクスチャ (埋め込み)・Display の設定・`grip` も読む。
- `itempack/ItemModelMaker`: ゲームのモデル (elements・textures・display・gui_light) を作る。持ち方は、バニラの値 (item/handheld・handheld_rod・bow・generated、block/block、1.20.4) に、モデルを「バニラの絵の代わり」として当てはめて出す: モデルの点 p は「バニラの値 ∘ (置き場所 + 大きさ × 回転 × (p − 基準の点))」に行く。手では、基準の点 = grip、置き場所 = バニラの剣の絵で手が持つ点 (三人称の値が手に置く点)、回転 = 立てたモデルを絵の斜めにする -45 度 (z)。スロットなどでは、基準の点 = 回したモデルの箱の真ん中、大きさ = 四角に収める倍率。
- `itempack/ItemPackBuilder`: アイテムごとにモデル (`bbmodel/<モデル>/<番号>.json`。アイテムごとに持ち方が違うので、1 アイテム 1 ファイル) と、モデルのテクスチャ (モデルごとに 1 回) を入れ、基盤のアイテムに override を足す。番号は `texture:` と同じ `item-cmd-index.yml`。
- 見た目が変わったアイテム (custom model data が新しく付く・変わる) は、アイテムの更新 (docs/item-patch.md) で、手元のアイテムも新しくなる。
- テスト: `ItemModelMakerTest` (読み・回転・持ち方の計算・パック)。`./gradlew test -PitemModelSamples=<.bbmodel のフォルダ>` で、そのモデルのパックを `build/item-model-samples/` に作る (目で見るため)。
