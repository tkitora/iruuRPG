# 敵のビヘイビアツリー + ステートマシン (BT+FSM)

敵の行動を、Unreal のビヘイビアツリー (Composite / Decorator / Task / Service) とステートマシンで書けます。**今までの動き(legacy)が既定**で、敵の yml に `behavior:` を書いて `mode: bt` にした敵だけが、この仕組みで動きます(`mode: legacy` にするか、`behavior:` を消すと、元の動きに戻ります)。コードは `mob/behavior/`。

## 切り替え
```yaml
mobs:
  my_mob:
    ai: true              # ai: false の敵には効かない
    behavior:
      mode: bt            # legacy (既定) | bt
```
`mode: bt` の敵は、バニラの行動目標(ゴール)が外れ、ツリーが歩く・殴る・撃つ・スキルを出す。ダメージ・スキル・遠距離攻撃(`ranged:`)は、これまでの敵と同じものを使う。

## テンプレートを参照する (use)
行動のひな形を `bt-templates/<id>.yml` に置いて、敵が**コピーではなく参照**で使える。ひな形を直すと、次の読み直しで、それを使う敵すべてに反映される(すでに出ている敵は、次に湧くときから新しい行動)。
```yaml
# bt-templates/chase_hit.yml
name: "追いかけて殴る"
description: "近づいて殴る基本形"
behavior:            # 敵の behavior: と同じ書き方 (mode: bt、fsm か tree、eqs、blackboard、debug、tick-interval、aggro-range、auto-target)
  mode: bt
  tree: {...}
```
```yaml
# 敵の側
behavior:
  use: chase_hit         # bt-templates/chase_hit.yml の behavior を使う
  debug: true            # 以下は、テンプレートの値の上書き(任意)
  aggro-range: 25
  blackboard: {taunted: true}   # テンプレートの blackboard に、キーごとに足す・置き換える
  eqs: {...}                    # 同じ名前の問い合わせは敵のもので置き換え、ない名前は足す
```
- `use` のときは `fsm` と `tree` は書けない(書いてあれば警告して、テンプレートのものを使う)。テンプレートの中の `use` は使えない(警告して無視)。
- `debug` `tick-interval` `aggro-range` `auto-target` `mode` は、敵に書いた値がテンプレートの値を置き換える。`mode: legacy` と書けば、その敵だけ今までの行動に戻る。
- `use` の id がない、またはテンプレートが使えない(`behavior` がない・ツリーも状態機械もない)ときは、読み直しレポートに警告(「敵 xxx の behavior.use 'yyy' がありません」)が出て、その敵は今までの行動(legacy)で動く。

## behavior: の項目
| 項目 | 内容 | 既定 |
|---|---|---|
| `mode` | `legacy` / `bt` | `bt` (`behavior:` を書いたとき) |
| `debug` | `true` で、頭の上に状態・動いているノード・目標・HPを出す | false |
| `tick-interval` | 何tickに1回ツリーを動かすか | 4 |
| `aggro-range` | この距離のプレイヤーを自動で目標にする | 20 |
| `auto-target` | 目標がいないとき、自動で探す | true |
| `blackboard` | 最初から持つ値 (`キー: 値`) | なし |
| `eqs` | 場所を選ぶ問い合わせ (下記) | なし |
| `navigation` | 最初の動き方。`walk` (歩く。バニラの経路探索) / `fly` (飛ぶ。下記「飛ぶ敵」)。`{mode: fly, speed: 6, walk-speed: 2.2}` の形で、速さ 1 のときの 1秒に飛ぶブロック数 (`speed`、`fly-speed` とも書ける) と歩くブロック数 (`walk-speed`) も書ける。途中で変えるのは `set-navigation` (下記「飛ぶ・歩くの切り替え」) | walk (飛ぶ速さ 5、歩く速さは敵の種類のまま) |
| `volume` | 動ける範囲 (出た場所が中心。下記「動ける範囲」) | なし (fly のとき・`set-navigation` で飛ぶときは円柱: 半径 = 黒板の `leash` (無ければ 32)、上 24・下 8) |
| `waypoints` | `patrol` の巡回ルート (下記) | なし |
| `fsm` | ステートマシン (`initial` と `states`) | |
| `tree` | `fsm` を使わないときの、1本のツリー | |

`fsm` と `tree` のどちらかが要る。`tree` だけなら、状態が1つ(`root`)のステートマシン。

## ステートマシン
```yaml
fsm:
  initial: idle
  states:
    idle:
      tree: {...}            # この状態のとき動くツリー
      services: [...]        # (任意) この状態のツリー全体の Service
      transitions:           # 上から順に調べ、最初に成り立ったものへ移る
        - {to: combat, when: {type: blackboard, key: has-target, op: "==", value: true}}
```
`when` は Decorator の条件 1つ、またはその一覧(全部成り立つとき移る)。ツリーの中の `set-state` でも移れる。
状態には `enter:`(ノード1つ)を書ける: その状態に入るたびに1回、ツリーより先に、終わるまで動く(その間は移り方も調べない)。ボスの「怒り」の咆哮・構え直しなど。

## ノード
全ノード共通: `type`、`name` (デバッグ表示の名前)、`decorators: [...]`、`services: [...]`。

### Composite (`children: [...]` を持つ)
| type | 動き |
|---|---|
| `selector` | 子を上から順に試し、失敗しなかった最初の子の結果で終わる。全部失敗したら失敗。上の子に `abort: lower` の Decorator があれば、それが成り立った瞬間に、動いている下の子を止めて上の子へ戻る |
| `sequence` | 子を上から順に動かす。1つでも失敗したら失敗、全部成功で成功 |
| `random` | 入るたびに子を1つ選んで動かす |
| `parallel` | 1つ目の子(主)と2つ目の子(裏)を同時に動かす。主が終わると裏を止めて終わる |

### Task (葉)
| type | 項目 | 動き |
|---|---|---|
| `move-to` | `target: target\|home` / `key: <黒板のキー>` / `eqs: <問い合わせ>`、`speed` (1.0)、`acceptance` (1.5)、`timeout` 秒 (10)、`face` (false)、`height` (0) | `height` は、その場所の何ブロック上へ行くか (飛ぶ敵が目標の上に留まる、など)。近づく。経路がない・2秒進まない・時間切れで失敗。`face: true` で、動くあいだ目標のほうを向いたまま (向きを変えずに後ろ・横へ下がる。間合いを取るとき用)。モデルのある敵はモデルごと、ない敵は頭だけ向く |
| `flee` | `eqs` (組み込みの `flee`)、`speed` (1.3)、`face` (false) | 目標から遠い、歩ける場所へ逃げる。`face` は `move-to` と同じ |
| `wait` | `seconds` (1)、`random-deviation`、`key` | 止まって待つ。`key` の黒板の値(秒)があれば、それを使う(1本のツリーで、敵ごとの間合い・攻撃の間隔を変える) |
| `attack` | `rate` (1.0)、`range` | 目標が range 内なら近接で殴る。外なら失敗。`range` を書かないと、黒板の `attack-range`(無ければ 2.6) |
| `shoot` | `rate` | `ranged:` の攻撃を1発撃つ(`ranged:` がないと失敗) |
| `use-skill` | `skill`(スキルツールのスキルの id。skills/*.yml)、または `index`(敵の `skills:` の番号)。**どちらも省略すると、敵の `skills:` のうち、クールダウン(`cooldown-ticks`)が明けていて、いまの距離・見え方で届くものから1つ**(ツリーを使わない敵と同じ選び方。無ければ失敗)、`animation`(同時に再生するモデルのアニメーション)、`track` (false) | スキルを出して、終わるまで(アニメーションがあれば、それも終わるまで)動く。`skill:` は、敵の `skills:` に同じ id があればそれ、無ければスキルツールのスキルを使う(敵の `skills:` に書かなくてよい)。当たる瞬間は、スキルの溜め(`windup-ticks`)で決まるので、アニメーションの振り下ろしの時刻に合わせる。**敵の攻撃は、まずスキルツールのスキルで作り、`use-skill` で呼ぶ**(範囲・溜めの表示・ダメージ・ノックバック・状態異常が、スキルの画面で編集できる)。スキルで作れないものだけ `animation-attack`。**敵はスキルの間、プレイヤーの方へ回らない**(始めた瞬間に1回向いて、その向きのまま。溜めの間に出る範囲も動かない)。`track: true` で、溜めの間もプレイヤーを向き続ける(範囲も一緒に動く)。ツリーを使わない敵のスキルも、回らない |
| `orbit` | `radius` (0 = 始めたときの距離のまま、最小 2)、`speed` (0.5 = ふだんの半分)、`seconds` (2)、`random-deviation`、`direction: random\|left\|right` (random) | 目標のまわりを、同じ向きに回る。回るあいだ目標のほうを向いたまま (横歩き。モデルのある敵はモデルごと、ない敵は頭だけ)。時間がたつと止まって目標を向き、成功。壁などで進めないと逆回りに変える。目標がいない・まったく動けないと失敗。**スキルが使えず、殴れもしない(何もすることがない)ときの枝**に置く |
| `patrol` | `route` (`waypoints` の名前)、`speed` (0.8)、`wait` 秒 (0、各点で待つ)、`acceptance` (1.2)、`laps` (0 = ずっと) | 巡回ルートを、いちばん近い点から回る。行けない点 (経路がない・2秒進まない) は飛ばす。`laps` 周したら成功。ルートがないと失敗。歩く敵・飛ぶ敵のどちらでも使える |
| `set-navigation` | `mode: fly\|walk`、`rise` (0)、`land` (true)、`speed` (1.0)、`timeout` 秒 (4) | 動き方を変える。`fly`: 飛び立ち (重力なし・自分の舵取り)、`rise` ブロック上がる。`walk`: `land: true` なら、真下の地面まで飛んで降りてから歩く (false なら、その場で落ちる)。上がる・降りる間は動く。時間切れでも切り替えはして成功。もうその動き方なら、すぐ成功。飛べない (範囲が作れない) ときだけ失敗。下記「飛ぶ・歩くの切り替え」 |
| `face-target` | | 目標のほうを向く |
| `set-blackboard` | `key`, `value` | 黒板に値を書く |
| `set-state` | `state` | ステートマシンの状態を変える |
| `clear-target` | `seconds` (5) | 目標を忘れ、しばらく探さない |
| `summon` | `mob` (必須), `count` (2, 1〜10), `radius` (4, 1〜16), `max-alive` (6, 1〜30) | 自分のまわり `radius` ブロックの地面に、モブ `mob` を `count` 体呼ぶ。自分が呼んだものが生きているのは `max-alive` 体まで。呼ばれたものは、今の目標を狙う。呼んだモブが死ぬ・消えると、一緒に消える (ドロップなし)。呼べた・上限に達していたら成功、モブが無い・置ける場所が無いと失敗 |
| `play-sound` | `sound`, `volume`, `pitch` | 音を鳴らす |
| `play-animation` | `animation`、`wait` (false) | モデルのアニメーションを1回再生する(`wait: true` で終わるまで待つ)。モデルが無い敵では何もせず成功 |
| `animation-attack` | `animation`、`hits: [{at 秒, shape: circle\|cone\|line, forward, radius, angle, width, height, rate, knockback}]`(1つなら hits を省いて直接書ける)、`telegraph` (false)、`face` (true)、`duration` 秒 (1、モデルが無いとき) | 止まって目標を向き、モデルのアニメーションを再生し、各ヒットの `at` 秒の瞬間に、その範囲のプレイヤー全員に当てる(`rate` 倍のダメージと `knockback`)。アニメーションが終わるまで動く。`telegraph: true` で、当たる範囲を当たるまで地面に赤く出す。範囲は敵の向きから: `circle` は `forward` 先を中心に半径 `radius`、`cone` は敵から半径 `radius`・角度 `angle`、`line` は `forward` 先から長さ `radius`・幅 `width`。上下 `height` (3) を超えた人には当たらない |
| `succeed` / `fail` | | 何もしないで成功 / 失敗 |

### Decorator (`decorators:` に並べる。`abort: none\|self\|lower\|both` を付けられる)
| type | 項目 | 動き |
|---|---|---|
| `blackboard` | `key`, `op` (`==` `!=` `<` `<=` `>` `>=` `set` `not-set`)、`value`、`value-key` | 黒板の値の条件。`value-key` の黒板の値があれば、`value` の代わりにそれと比べる(敵ごとの限度: `home-distance > leash` など) |
| `distance` | `min`, `max`、`min-key`, `max-key` | 目標までの距離の条件(目標がいないと不成立)。`min-key` / `max-key` の黒板の値があれば、それを境にする |
| `hp` | `below`, `above` (0〜1) | 自分のHPの割合の条件 |
| `line-of-sight` | `visible` (true) | 目標が見えているか |
| `chance` | `percent` | 入るときだけ確率で通す |
| `cooldown` | `seconds`、`on: any\|success` (any) | 終わってから、その間は入れない。`on: success` は、成功で終わったときだけ待つ(スキルを出せたときだけ「スキルを使わない時間」を始め、使えるスキルが無くて失敗したときは、次の機会にすぐ試せる) |
| `allies` | `radius` (8)、`min` (0)、`max`、`same-type` (true) | まわりの仲間(同じ種類の、動いている敵)の数の条件。囲い込みの判断・仲間が少ないとき下がる、など |
| `slot` | `name` (attack)、`max` (2)、`hold` 秒 (3)、`per: target\|type\|world` (target) | **攻撃の枠(トークン)**。同じ目標(`per`)に向かう、同じ名前の枠を、仲間と共有する。入れるのは、枠が `max` 未満のとき(入ると枠を持ち、ノードが終わる・止まる・`hold` 秒たつと返す)。取れないときは不成立になり、`selector` の次の枝(回り込む・待つ)へ進む。「同時に殴るのは2体まで」はこれ。枠を返した敵が、すぐ取り直さないよう、枝に `cooldown` も付けるとよい |
| `group-cooldown` | `key` (group)、`seconds` (6)、`scope: target\|type\|world` (target) | 仲間で共有するクールダウン。1体が入ると、`scope` の仲間すべてが、その間は入れない。大技が同時に出ないようにする |
| `loop` | `count` / `infinite: true` | 繰り返す |
| `time-limit` | `seconds` | 動き続けたら失敗にして止める |
| `not` | | 成功と失敗を入れ替える |
| `force-success` | | 結果を成功にする。同じノードの条件 (`distance` など) が合わずに入れなかったとき、条件や `time-limit` で止まったときも成功になる (「できればやる、できなくても先へ進む」) |
`abort: self` は、条件が成り立たなくなったら動いているノードを止める。`abort: lower` は、`selector` の上の子で使い、成り立った瞬間に下の子を止める(`both` は両方)。

### Service (`services:` に並べる。動いている間、`interval` 秒ごとに実行)
| type | 項目 | 動き |
|---|---|---|
| `update-target` | `range` (20)、`interval` (0.5) | 目標を保ち、いなければ最も近いプレイヤーを選ぶ |
| `run-eqs` | `query`, `key` (point)、`interval` (1) | 問い合わせの結果の場所を黒板の `key` に書く(`move-to` の `key:` で使う) |

## 黒板 (Blackboard)
ランナーが毎回更新するキー: `has-target` (真偽)、`distance` (目標まで)、`in-sight` (目標が見えるか)、`hp` (0〜1)、`state` (いまの状態)、`home-distance` (出た場所からの距離)、`in-volume` (動ける範囲の中にいるか。範囲がなければ true)、`flying` (いま飛んでいるか)、`altitude` (足元の地面からの高さ。32 まで。飛んでいるときだけ)、`allies-near` (まわり `ally-range` (既定8)ブロックにいる、同じ種類の動いている敵の数)、`attackers` (同じ目標に、いま攻撃の枠を持っている仲間の数)。ほかは `blackboard:`・`set-blackboard`・`run-eqs` が書く。

**`phase` (ボスのフェーズ)**: 黒板の `phase` に 1、2、3 … を書くと、それがボスのフェーズとして、ダンジョンの BGM に使われる (`docs/bgm.md`: フェーズが進むと、ボス戦の次の曲に変わる)。最初は `blackboard: {phase: 1}`、次の状態の `enter` で `set-blackboard` の `key: phase, value: 2`。書かないボスは、フェーズ1のまま。

## 場所を選ぶ問い合わせ (EQS)
```yaml
eqs:
  flank_spot:
    generator: {type: ring, around: target, radius: 4.5, points: 12}   # ring (rings: 輪の数) | grid (spacing) | sphere (球) | self
                                                                        # offset-y: 中心からの高さ、layers: 縦に重ねる段の数 (1)、height: 段を offset-y ± height に並べる (radius の半分)
    tests:
      - {type: distance, to: self, prefer: near, weight: 1.0}          # prefer: near | far、value: <数> でその距離に近いほど高得点、filter: true と min/max で範囲外を外す
      - {type: line-of-sight, to: target, filter: true}                # visible: false で「見えない場所」
      - {type: crowd, value: 3, max: 1, filter: true}                  # まわり(value ブロック)にいる、同じ種類の敵の数
      - {type: random, weight: 0.3}
      - {type: spread, to: target, value: 60, weight: 1.0, filter: false, min: 30}   # 囲い込み: `to` (target / home。省略は target) を中心にした角度で、仲間のいる角度から離れた場所ほど高得点 (value = 十分離れたとみなす角度差(度)、min と filter: true でそれより近い場所を外す)
      - {type: ally-distance, prefer: far, min: 0, max: 24, weight: 1.0}                # いちばん近い仲間との距離 (prefer: near | far、filter: true と min / max で範囲外を外す)
      - {type: height, value: 5, weight: 1.0}                          # 地面からの高さ (prefer: near | far | value、filter と min / max)。飛ぶ敵の高度
      - {type: pathable}                                               # 行ける場所だけ (歩く敵は歩いて、飛ぶ敵は飛んで。点の高い順に数個だけ調べる)
    pick: best          # best | random-top (上位 top 個から乱択)
    top: 3
    place: auto         # auto (歩く敵は地面の上、飛ぶ敵は空中) | ground (飛ぶ敵でも地面の上)
```
歩く敵の候補は地面の上に置かれ(地面がない場所は捨てる)、飛ぶ敵の候補は空中の、体が入る場所に置かれる(ブロックの中なら上下・横の近い場所へずらし、無ければ捨てる)。動ける範囲 (`volume`) の外の候補は捨てる。`around` は `self` / `target` / `home`。組み込みの `flee` は、自分のまわりで目標から遠い、行ける場所。

`sphere` は、`around` を中心にした半径 `radius` の球の上に、`layers` 段 (既定 5) の輪で候補を並べる (極に近い段ほど小さい輪。`rings` で内側の輪も足せる)。`ring` / `grid` に `layers` を付けると、同じ並びを縦に重ねる。

## 動ける範囲 (volume)
```yaml
volume: {shape: cylinder, radius: 16, up: 14, down: 2}   # cylinder (円柱) | sphere (球: radius だけ) | box (箱: 東西南北に radius、上 up・下 down)
```
出た場所 (home) が中心。歩く敵では、EQS の候補を範囲の中だけにし、黒板の `in-volume` で「範囲の外に出たら戻る」を書ける。飛ぶ敵では、そのうえ経路も範囲の中だけを通り、範囲の外の場所へ行けと言われたら、範囲の縁のいちばん近い場所へ行く (目標が範囲の外に逃げると、縁まで追って止まる)。テンプレートを `use` する敵が `volume` を書くと、テンプレートの `volume` を丸ごと置き換える (敵ごとに範囲を変えられる)。

## 飛ぶ敵 (navigation: fly)
```yaml
behavior:
  navigation: {mode: fly, speed: 6}
  volume: {shape: cylinder, radius: 16, up: 14, down: 2}
```
- 重力が無くなり、バニラの移動 (経路探索・移動の制御) は使わない。ツリーが行き先を決め、毎 tick、速度で飛ぶ向きへ動かす (体は飛ぶ向き、`face: true` や `orbit` のときは目標のほうを向く)。
- 経路: まっすぐ飛べるなら直線。ブロックがじゃまなら、範囲の中のブロックの格子で 3D の A* (上下・斜めを含む 26 方向。角はすり抜けない。体の幅と高さが入るところだけ) を解き、折れ点だけの経路に縮める。1回に調べるのは 3000 マスまで (届かないときは、いちばん近づける所まで)。
- `move-to` `flee` `orbit` `patrol` と EQS (`pathable` を含む) は、そのまま飛ぶ敵でも使える。`move-to` の `height` で、目標の上に留まれる。`attack` の届く距離は立体の距離。
- 敵の種類 (`type:`): 何でもよい (モデルの土台の見えない敵も可)。ただし **VEX は壁をすり抜ける**ので向かない。PHANTOM は日光で燃えるので `fire-proof: true` を付ける。
- スキルは、空中の敵の位置から出る (地面の予告は、敵の足元の高さに出る)。
- `/iruurpg btdebug` で、飛ぶ敵の経路 (白い粒の線) と、動ける範囲の縁 (青い輪。円柱と箱は 下・出た場所・上 の 3段) が見える。
- 突進 (`dash`) のスキルは、飛んでいる間は、目標へまっすぐ (上下も) 突っ込む (急降下)。地上では今までどおり水平に跳ぶ。
- モデルに `fly` アニメーションがあれば、飛んでいる間は `walk` / `idle` の代わりに `fly` を流す。よろけ (`hurt`) は、飛んでいる間は `fly_hurt` を再生する (無いモデルは、飛んでいる間はよろけない。地上用の `hurt` では体が地面の高さに下がってしまうため)。

## 飛ぶ・歩くの切り替え (set-navigation)
飛ぶ時間と歩く時間のある敵 (空と地上の 2 形態のボス、など) は、`set-navigation` で途中から変える。状態 (ステート) ごとに分けて、`enter:` で切り替えると分かりやすい。
```yaml
behavior:
  navigation: {mode: walk, speed: 7, walk-speed: 2.2}   # 最初は歩く。飛ぶ速さ 7、歩く速さ 2.2 ブロック/秒
  volume: {shape: cylinder, radius: 20, up: 12, down: 4}
  fsm:
    initial: ground
    states:
      ground:
        tree: ...                                        # 地上の技
        transitions: [{to: air, when: {type: blackboard, key: go-air, op: "==", value: true}}]
      air:
        enter: {type: set-navigation, mode: fly, rise: 5}  # 飛び立って 5 ブロック上がる
        tree: ...                                        # 空中の技
        transitions: [{to: land, when: ...}]
      land:
        enter: {type: set-navigation, mode: walk}          # 真下に降りてから歩く
        tree: {type: set-state, state: ground}
```
- 黒板の `flying` で、いまの動き方を条件にできる。
- `navigation` の `walk-speed` は、歩く速さ (`move-to` などの `speed: 1` のとき) を 1秒に何ブロックかで決める (敵の移動速度を、その速さになるように変える。普通の地面での目安。`speed: 1.2` なら約 1.44 倍)。書かなければ、敵の種類のまま。

## 巡回ルート (waypoints)
```yaml
waypoints:
  lap:
    points: [[8, 6, 0], [0, 8, 8], [-8, 6, 0]]   # 出た場所から見た点 [x, y, z] ({x:, y:, z:} でもよい)
    mode: loop                                   # loop (一周して最初へ) | back-and-forth (行って戻る)
    absolute: false                              # true で、点をワールドの座標として読む
  short: [[3, 0, 0], [-3, 0, 0]]                 # 点の一覧だけでもよい (loop)
```
`patrol` が使う。テンプレートを `use` する敵は、同じ名前のルートを置き換え、ない名前を足せる。

## 確認用の敵
`/iruurpg mob bt_sample_flyer` (飛ぶ敵: 見張りは空中の巡回、戦闘は目標の上空 (EQS) で様子を見て降下して殴り、上空へ戻る)。
`/iruurpg mob bt_sample_zombie` (`mobs/behavior_sample.yml`)。`debug: true` で、頭の上に **状態 / 動いているノードの経路 / 目標・距離・HP** が出る。状態は `idle` (見張り) と `combat` (戦闘) の2つで、戦闘は上から 逃走 (HP25%以下) → スキル (近い・8秒に1回) → 近接 → 回り込み (EQS で横へ) → 追跡。

## スキルのカテゴリ
スキル(skills/*.yml)には `category:` を書ける(例: `category: "ボス/ボススケルトン"`)。ツールサイトのスキルの一覧が、カテゴリごとにまとまる(書かないスキルは、今までどおり、まとめずに並ぶ)。プラグインは `category` を使わない(読み飛ばす)。

## 実行中の状態を見る (コマンド)
`/iruurpg btdebug [on|off]` (管理者): 自分にだけ、近くのビヘイビアツリーの敵すべての頭の上に、**状態 / 動いているノードの経路 / 目標・距離・HP / 問い合わせ(EQS)の結果**を出す(確認用の敵と同じ表示)。敵の `debug:` の設定には関係なく出せる。表示中は、問い合わせの**候補点**が粒で出る(点数が高いほど緑・低いほど赤、テストで外された点は灰色、選ばれた場所は光の柱)。もう一度打つとオフ。表示距離は `mob.behavior-view.range` (40)。

## ツールサイトの項目 (yml 担当へ)
敵の編集画面に、**行動の切り替え**(既定は「今までの行動」、もう1つが「BT+FSM」)を足す。「BT+FSM」を選んだときだけ、上の `behavior:` を編集する欄を出す(状態の一覧、状態ごとのツリー、ノードの `type` と項目、Decorator・Service、問い合わせ)。保存形式は上の yml そのまま。切り替えを「今までの行動」に戻すと `behavior:` を書き出さない(または `mode: legacy`)。

飛ぶ敵のための欄: `navigation` (歩く / 飛ぶ、飛ぶ速さ、歩く速さ `walk-speed`)、`set-navigation` ノード (mode・rise・land・speed・timeout)、`volume` (形・半径・上・下)、`waypoints` (ルートの名前・点の一覧・loop / back-and-forth・absolute)、`patrol` ノード (route・speed・wait・acceptance・laps)、`move-to` の `height`、EQS の `sphere` と `offset-y` / `layers` / `height`、`place`、`height` テスト。
