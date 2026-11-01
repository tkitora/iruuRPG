# スキル (小・中・大・util・ULT、2026-11-02 の改編)

ユーザーの改編 (設計書はワークスペースの `design/skill-redesign-2026-11-02.md`)。t116〜t140 の「スキル1〜6 + 必殺 + アイテムスキル、F・Q の 2 キー 8 通り」を置き換えた。

## キー
武器の「通常のボタン」は、近接は左、遠距離・設置は右。もう一方を「別のボタン」とする。

| 動作 | キー | 近接 | 遠距離・設置 |
|---|---|---|---|
| 小 | Q → 通常のボタン | Q→左 | Q→右 |
| 中 | Q → 別のボタン | Q→右 | Q→左 |
| 大 | Q → Q | Q→Q | Q→Q |
| util | F | F | F |
| ULT | Shift + F | Shift+F | Shift+F |
| アイテムスキル | 別のボタンだけ | 右 | 左 |
| 通常攻撃 | 通常のボタンだけ | 左 | 右 |

- Q は、`combat.skill-slots.key-window-ticks` (20 tick) の間、2 つ目のキーを待つ。Q だけでは何も出ない。F は Q の待ちを消す。
- キーは武器を持っているときだけ。Shift+Q (設置の武器) は、設置を消す。
- 別のボタンの押しっぱなしは、アイテムスキル 1 回。
- 読むのは `combat/SkillInput`、出すのは `listener/CombatListener#skillClick`・`onSwapKey`・`onDropKey`。

## クールタイム
`combat.skill-slots.cooldown-seconds` (秒)。`default` が全クラス、クラスの id (warrior / mage / healer / gifted / assassin) の下はそのクラスだけ。設定が無ければ設計書の値 (`SkillSlots.DESIGN`)。プレイヤーのスキルのクールタイム短縮 (`skillCooldownMultiplier`) も掛かる。

| | 小 | 中 | 大 | util | ULT |
|---|---|---|---|---|---|
| 全クラス | 7 | 12 | 25 | 5 | 100 |
| 戦士 (warrior) | | 10 | 30 | | |
| クレリック (healer) | | | | 15 | |
| レンジャー (gifted) | 3 | 7 | 20 | | |
| アサシン (assassin) | | | 20 | | |

## クラスの技
- `SkillSlots#register(クラス, スロット, Move)` で登録する。`Move#use` が true を返したら、そのスロットのクールタイムが始まる。クールタイム中は鈍い音だけで、何も出ない。
- 登録されていないスロットは、今までのもの (`listener/SkillFallback`)。
  - 小・中・大: 持っている武器のスキル1〜3 (以前の強1〜強3)。クールタイムもそれぞれのもの。
  - ULT: クラススキル (解放ノードの F の技)。クールタイムは、クラススキルの yml の値の代わりに ULT の値 (100 秒)。
  - util: まだ無い (押すと「まだありません」、アイコンは鍵)。
- 技は、各クラスの得意武器 1 種に合わせて作る (クラスごとに 1 組)。得意でない武器は、テンプレートでいったん埋める: 小・中・大・util = その武器のスキル1〜4。
- 今までのスキル (強攻撃) を使う技は `combat/SlotMoves` に、得意武器と、小・中・大・util が出すスキルの番号を書く (`AttackService#slotHeavy` で出す。強攻撃のクールタイムは使わず、スロットのクールタイムだけ)。コンボ解説には「小 = スキル6 / …」と出し、アイコンの仮の絵もそのスキルの絵になる。

| クラス (得意武器) | 小 | 中 | 大 | util |
|---|---|---|---|---|
| メイジ (遠距離) | スキル6 連鎖する雷 | スキル4 結晶の棘 | スキル5 縮む球体 (ブラックホール) | スキル1 再発動 (再発動するものが無いときは、クールタイムは始まらない) |

## アクションバー
武器を持っていてゲージが出ているとき、ゲージの上に 小・中・大・util・ULT のアイコン、半分あけてアイテムスキルのアイコン。上にキーの表示 (持っている武器での左右)。Q が待っている間は、小・中・大 のキーが黄色、ほかは灰色。

絵 (16×16、`src/main/resources/hud/`、テクスチャ担当の名前):
- `small_<class>.png`・`medium_<class>.png`・`large_<class>.png`・`util_<class>.png`・`ultimate_<class>.png` (`<class>` は warrior / assassin / mage / gifted / healer / generic)
- `itemskill.png`・`itemskill_locked.png`・`lock.png`
- 小・中・大 の絵が無いときは、スキル1〜3 の絵 (`heavy_<class>_<n>.png`)。util の絵が無いときは、仮の絵 (クラスの色に二重の矢印)。
- キーの表示は、プラグインが描く (Q→左・Q→右・Q→Q・F・Shift+F・左・右 の 7 枚)。
