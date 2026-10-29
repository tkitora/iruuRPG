# モデル機能 (Blockbench のモデルを敵に着せる)

ModelEngine のような機能。Blockbench で作ったモデル (`.bbmodel`) を、敵に着せて、アニメーションさせる。コードは `model/`、プロシージャルツールと同じく、**専用のパッケージ・フォルダ・設定・コマンドだけ**で動く。

## しくみ
1. `plugins/iruuRPG/models/<id>.bbmodel` を読む(id = ファイル名)。キューブ(回転・Box UV/自由UV)、グループ(ボーン)の原点と回転、テクスチャ(**埋め込み**で保存すること)、アニメーション(回転・位置・拡縮のキーフレーム。補間は linear / catmullrom / step)。メッシュ・ロケーター・モラン式は対象外(警告を出して飛ばす)。
2. モデル専用のリソースパック `resourcepacks/iruumodel.zip` を作る(`/iruumodel build`、起動時にも作る)。ボーンごと(回転したキューブは1つずつ)のアイテムモデルを `PAPER` のモデルの override(custom model data)に足す。部品の番号は `models/index.yml` に残り、モデルを直しても同じ。override は番号の小さい順に並べる(ゲームは「番号が部品の番号以下の override のうち最後のもの」で描くので、順が乱れると、あとから足したモデルの部品が、ほかのモデルの部品で描かれる)。**サーバー専用パック(`server.zip`)とは別のパック**(docs/resourcepacks.md)。
3. 敵に着せる: 敵の体を見えなくして(持ち物・防具も、周りのプレイヤーには空に見せる。名前と HP はモデルの頭の上に出す)、ボーンごとのアイテムディスプレイを敵の位置・向きに合わせて動かし、アニメーションを再生する。歩いていれば `walk`(敵の実際の速さに合わせて再生の速さを変える: 下の「歩きの速さ」)、止まっていれば `idle`、よろけたときに `hurt`(下の「体幹」。攻撃などの途中は止めない)、死ねば `death`(終わると煙を出して縮んで消える。秒数は config の `model.death-vanish-seconds`、0.6。0 で煙なし)。アニメーション名は、そのままの名前のほか、`animation.idle` のような接頭辞つき、`move` / `run`(= walk)、`attack_slam` のような `attack` で始まる名前(乱択)も読む。ビヘイビアツリーの敵は `attack` と `skill` の動きで、それぞれのアニメーション(`skill` が無ければ `attack`)。

## 使い方
```yaml
mobs:
  my_mob:
    type: SPIDER          # 当たり判定・体力などの土台
    model:
      id: sample_crawler  # models/sample_crawler.bbmodel
      scale: 1.0
      hide-base: true     # false で、土台の体も見せる
```
- ビヘイビアツリーでは `{type: play-animation, animation: attack, wait: true}`(`wait` はアニメーションが終わるまで待つ)。モデルが無い敵では何もせず成功するので、モデルを外しても木は動く。
- ツールサイトの敵の編集画面から作る敵にも、同じ `model:` を書ける(yml をそのまま使う)。
- 確認: `/iruumodel demo <id> [scale]`(自分の場所に出す)→ `/iruumodel play <アニメーション>` → `/iruumodel clear`。**パックを入れていないクライアントでは見えない**(`/iruurpg pack`)。
- 見本: `models/sample_crawler.bbmodel`(初回に自動で置く。idle / walk / attack / death)と、敵 `model_sample_crawler`(`mobs/model_sample.yml`、`/iruurpg mob model_sample_crawler`)。

## Blockbench での作り方
- 形式は **Generic Model(Free)**。向きは前が `-Z`(北)。足元が `y = 0`。大きさは 16 = 1ブロック。
- ボーン = グループ。アニメーションを付けたいものは、グループにする。キューブはどれかのグループに入れる(グループ外は原点のボーンになる)。
- 部品(ボーン)のキューブが原点から遠い(長い剣など)ときは、自動で部品の真ん中を中心に描き、48ユニットより長ければ小さく作って表示で拡大する(バニラのモデルの範囲 -16〜32 に収めるため)。
- 回転したキューブ(傾いた尻尾など)は、1つずつ別の部品として表示される(バニラのモデルは1軸の22.5度刻みしか回せないため)。部品が増えると重いので、必要なものだけ回す。
- 回転・位置は Blockbench のビューアと同じ規則(アニメーションの回転は x・y が逆、位置は x が逆、Z→Y→X の順)。見た目が違うときは、`/iruumodel demo` で確かめて知らせてほしい。
- **歩き (`walk`) は秒速1ブロック (1 m/s) で歩く動きとして作る**。ゲームでは、敵の実際の速さ ÷ 1 m/s の倍率で再生する(2 m/s で歩く敵なら2倍速。足が地面を滑らない)。倍率は 0.25〜4 倍の間。基準は `model.walk-speed` で変えられる。モデルごとに別の速さで作ったときは、敵の `model:` に `walk-speed: 2.2` のように書く(その敵だけ、その速さを基準にする)。
- **飛ぶ (`fly`)**: 飛んでいる敵 (重力の無い敵。ビヘイビアツリーの `navigation: fly` / `set-navigation`) は、モデルに `fly` があれば、動いていても止まっていても `fly` を流す。飛んでいる間のよろけは `fly_hurt` (無ければ、飛んでいる間はよろけない)。
- 保存するとき、テクスチャを**埋め込む**(`Save Model` で ok)。パスだけだと読めない。

## 体幹 (hurt のよろけ)
すべての被弾で `hurt` を再生するのではなく、敵の中に体幹ゲージを持つ。当たるたびにたまり(1発ごとの固定量 + 最大HPに対するダメージの割合の分)、満タンになると**よろける**: `hurt` を再生し、その間(既定 0.35 秒)は止まる(歩かない・ツリーも攻撃も止まる。滑りながら hurt を再生しない)。大きなスタンではなく、既定では数発に1回の一瞬の硬直。攻撃などのアニメーションの途中では、よろけない(ゲージは満タンのまま、次の被弾でよろける)。よろけたあと 1.5 秒は、次のよろけは無い。当たらないまま 2.5 秒たつと、ゲージは減り始める。
- 既定は `config.yml` の `poise:`(`max` 100、`per-hit` 30、`damage-scale` 100、`drain-delay` 2.5、`drain-per-second` 30、`flinch-seconds` 0.35、`min-interval` 1.5)。
- 敵ごとに `model:` の中の `poise:` で上書きできる(例: `model: {id: skeleton_boss, poise: {max: 160, flinch-seconds: 0.3}}`)。
- `hurt` アニメーションの無いモデルの敵は、よろけない。環境のダメージ(落下・溶岩など)では、よろけない。

## 設定 (`config.yml` の `model:`)
`enabled`(false でこの機能ごと止める)、`auto-build`、`base-item`(PAPER)、`cmd-base`(8100000)、`update-ticks`(2)、`view-distance`(48)、`walk-speed`(1.0: walk アニメーションが想定する速さ、ブロック/秒)。

## 消すには(ごみを残さない)
- **データを消す**: `/iruumodel uninstall`(確認のため2回)。すべてのモデルのディスプレイを消し、モデルを着た敵に体を返し、`models/` と `resourcepacks/iruumodel.zip` を消す。敵の `model:` の記述は消えない(機能が無いときは無視される)。
- **止める**: `model.enabled: false`。
- **コードを外す**: 次のものを消せば、残るものは無い。
  - `model/` パッケージ全体、`mob/MobModels.java`
  - `IruuRPG.java` の `modelModule`(3行: 宣言・start と `setModels`・stop)
  - `RpgMobService` の `models` / `setModels` と、`startBehavior` の前後の `models.attach` / `models.play` の数行と、`BtHost` の `playAnimation` / `animationSeconds`
  - `RpgMobDefinition` / `RpgMobRegistry` の `model`
  - `mob/behavior` の `BehaviorHost.playAnimation` / `animationSeconds`、`Tasks.PlayAnimation`、パーサーの `play-animation`
  - `plugin.yml` の `iruumodel`、`config.yml` の `model:`、`IruuRPG.java` の `ResourceSync.sync(this, "mobs/model_sample.yml")`、`resources/models/` と `resources/mobs/model_sample.yml`
