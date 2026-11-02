# 攻撃の音と、当たったときの演出 (t166)

## 音
カスタムの音のファイルは使わず、バニラの音をいくつか重ねて、ピッチを変えて作る。
- 通常攻撃・命中・スキルの一部は `combat/AttackSounds` で鳴らす。設定の音量 (近接攻撃・命中音など、プレイヤーごと) が掛かる。
- コンボの技・クラスの技は `combat/Sfx` (音の役割) で鳴らす。MoveKit が、クラスの音の系統に置き換える (`MoveKit#remap`)。
- **斬撃に、バニラの sweep (`ENTITY_PLAYER_ATTACK_SWEEP`) は使わない** (t166: 鈍器のような音だった)。鋭く風を切る音にした。
  - `BLADE`: 近接の素振り。トライデントを投げる音 (高め)、ファントムの羽ばたき (高め)、鎖の金属音 (小さく)。
  - `BLADE_HEAVY`: 戦士の斬撃。低めの風切り、ドラゴンの羽ばたき、鉄の音。戦士の斬撃は、今まで金属を叩く音 (金床) に置き換わっていた。
  - `WIND` (アサシンなどの技の斬撃)・`REAP`・`SHADOW_*` も、sweep をトライデントの風切りに替えた。
- **命中の音は、斬撃と別**。今までは、骨を叩く・パンチの鈍い音だった。
  - `HIT_CUT`: 近接の命中。トライデントが刺さる音、会心のパチッ、鎖が切れる音。
  - `HIT_PIERCE`: 遠距離の命中。矢の命中、刺さる音、水晶の音。
  - `HIT_CRIT`: 会心のときに足す音。
- **リーパー (メイジの大鎌、t167)** は、自分の音の役割を持つ (`REAPER_*`): 低い風切りと魂の息 (`REAPER_SWING`)、重い刈り (`REAPER_HEAVY`)、刻印がそろった音 (`REAPER_MARK`)、刈り取り (`REAPER_REAP`)、魂を取り込む音 (`REAPER_SOUL`)、回る鎌 (`REAPER_THROW`)、霊体のすり抜け (`REAPER_PHASE`)、門 (`REAPER_GATE`)、大鎌の 1 周 (`REAPER_GRAND`)。
- **戦士 (t168)** は、武器の種類ごとに自分の音の役割を持つ (戦士の置き換え `remap` は掛からない)。
  - 近接 (血の騎士) `BLOOD_*`: 重い刃の風切り (`BLOOD_SWING`)、肉を斬る音 (`BLOOD_HIT`)、旋風 (`BLOOD_WHIRL`)、叩きつけ (`BLOOD_SLAM`)、傷を取り返す音 (`BLOOD_DRINK`)、誓い (`BLOOD_OATH`) と心臓の音 (`BLOOD_HEARTBEAT`)、血が噴き出す音 (`BLOOD_BURST`)、受け流しの構え (`BLOOD_GUARD`) と受け流した音 (`BLOOD_PARRY`)。
  - 遠距離 (浮遊する大剣と鎖) `SWORD_*`・`CHAIN_*`: 大剣を呼ぶ音 (`SWORD_SUMMON`)、飛ぶ剣 (`SWORD_FLY`)、地面に刺さる剣 (`SWORD_IMPACT`)、巨大な剣 (`SWORD_GRAND`)、鎖を投げる音 (`CHAIN_THROW`)、縛る音 (`CHAIN_BIND`)、ほどける音 (`CHAIN_BREAK`)。
  - ランタン (孤立させる結界) `WARD_*`: 鉄の楔 (`WARD_WEDGE`)、鎖の柵 (`WARD_FENCE`)、檻 (`WARD_CAGE`)、断罪の鐘 (`WARD_BELL`)、孤立した敵に当たった音 (`WARD_ISOLATED`)。
- **クレリック (t168)** は、自分の音の役割を持つ。メイスと盾は鐘と打った鉄、杖は雷と澄んだ音。
  - メイスの振り (`HOLY_SWING`)、盾で殴る音 (`HOLY_BASH`)、盾を構えて動く音 (`HOLY_STEP`)、盾を掲げる音 (`HOLY_RAISE`)、構えた盾で受けた音 (`HOLY_BLOCK`)、審判の鉄槌 (`HOLY_HAMMER`)、守護の陣 (`HOLY_BASTION`)。
  - 聖光・聖句がたまりきった音 (`HOLY_FULL`)、祝福 (`HOLY_BLESS`)、癒しの光 (`HOLY_MEND`)、詠唱 (`HOLY_CHANT`)。
  - 杖の雷を呼ぶ音 (`STORM_CAST`)、雷が落ちる音 (`STORM_STRIKE`)。
- **レンジャー (t168)** は、自分の音の役割を持つ。ワイヤーは釣り糸と仕掛け線、弓はクロスボウの装填と弦の音、精霊弓は木とアレイ。
  - ワイヤーを投げる音 (`WIRE_THROW`)、張る・巻き取る音 (`WIRE_PULL`)、外れる音 (`WIRE_SNAP`)、ワイヤーで飛ぶ音 (`WIRE_ZIP`)。
  - 弓を引き絞る音 (`BOW_DRAW`)、放つ音 (`BOW_LOOSE`)。精霊弓を立てる音 (`SPIRIT_SET`)、全力射撃 (`SPIRIT_OVERDRIVE`)。
- **config で差し替えられる** (`sound-design`、読み直しで反映): 役割ごとに、`"音の名前 音量 ピッチ"` の行を並べる。書いた役割だけが変わる。読めない行や、無い役割は、ログに警告を出して飛ばす。
  ```yaml
  sound-design:
    BLADE: ["ITEM_TRIDENT_THROW 0.45 1.7", "ENTITY_PHANTOM_FLAP 0.25 2.0"]
    HIT_CUT: ["ITEM_TRIDENT_HIT 0.5 1.45", "ENTITY_PLAYER_ATTACK_CRIT 0.25 1.6"]
  ```

## 当たったときの演出 (`AttackEffects#playHitVfx`)
プレイヤーの攻撃が敵に当たるたびに出す (全クラス・通常攻撃も技も。ダメージの数字と同じ所)。
- 刃 (近接・設置): 敵の体を横切る、光の筋と火花。筋は、攻撃した人の見ている向きに対して、斜めの角度がランダム。
- 遠距離: 当たった所から飛び散る火花と、小さな球の輪。
- 会心: 筋が 2 本 (十字)。魔法の火花と、光も足す。
- 色は、武器の属性の色に鋼を混ぜたもの。
- 粒子の設定 (自分・ほかの人の粒子の量) に従う。
- `combat.effects.hit-vfx`: `enabled`、`size` (大きさ)、`per-tick` (1 人が 1 tick に出す数の上限。範囲攻撃で多数に当たっても重くならないように、6)。
