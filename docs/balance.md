# クラスのバランスの数値 (balance/<クラス>.yml)

2026-11-03、ユーザーの依頼で作りました。次の数値を、yml リポジトリ (iruuRPG/iruu_rpg_yml_file) の `balance/<クラス>.yml` で管理します。
- 各クラスのスキル (小/中/大/util/ULT) の倍率・クールタイム・ストックの数
- 通常攻撃のコンボの倍率 (`hit-rate`・`hit-counts`)

編集はツールサイトの「倍率」タブで行います (ログインが必要)。

## しくみ (`balance/BalanceService`)
- yml の同期 (`YmlSyncService`) がコミットを取り込んで読み直すたびに、`balance/*.yml` の数値を config の上に重ねます。
  - 重ねるのはメモリの中だけです。config.yml には書き込みません。
  - 読み直しは、起動・`/iruurpg reload`・コミットから約 1 分のときです。再起動は要りません。
- ファイルに無い項目は、サーバーの config.yml の値を使います。config.yml にも無ければ、jar の config.yml の値 (コードの既定値と同じ) です。
- ファイルを消すと、config.yml の値に戻ります。
- 重ねたあと、データフォルダに `balance-schema.json` を書き出します。中身は、ツールサイトが使う項目の一覧です。
  - 項目ごとに、置き場・config の値・使っている値・config.yml のコメント・範囲が入ります。

## ファイルの形
ファイル名はクラスの id です: `warrior` / `mage` / `healer` / `gifted` / `assassin`。
```yaml
combo:                       # combat.combo.<plan>.<key>。そのクラスのコンボだけ書ける
  warrior_melee:
    hit-rate: [0.6, 0.7, 0.45, 0.55, 1.3]   # 通常1〜5: 1回の倍率 (5つ、0.02〜5)
    hit-counts: [1, 1, 2, 2, 1]             # 通常1〜5: 当たる回数 (5つ、1〜20 の整数)
    small-rate: 0.5
cooldowns:                   # combat.skill-slots.cooldown-seconds.<クラス>.<slot> (small/medium/large/util/ult、0〜600 秒)
  ult: 100
ult:                         # そのクラスの ULT の cskill/<id>.yml の values (ファイルにある値だけ)
  lifesteal: 0.15
```

クラスごとのコンボ (plan):

| クラス | コンボ (plan) |
|---|---|
| warrior | warrior_melee、warrior (盾)、warrior_range、warrior_deploy |
| mage | mage (遠距離の通常攻撃)、mage_range、mage_deploy、mage_melee |
| healer | cleric_melee、cleric_range、cleric_deploy |
| gifted | ranger_melee、ranger_range、ranger_deploy |
| assassin | assassin_melee、assassin_range、assassin_deploy |

ULT は、warrior_strike / meteor / sanctuary / instinct_release / satsujin です。

## 受け付ける数値
- コンボの項目: jar の config.yml に、数値として書いてある項目だけです (`hit-rate`・`hit-counts` は全コンボ)。
- 範囲は、項目の名前で決まります (`BalanceService#rule`)。

| 項目の名前 | 範囲 |
|---|---|
| `*charges` | 1〜9 の整数 |
| `*-count` | 1〜 (50 か既定値の 10 倍の大きい方) の整数 |
| `*-ticks` | 0〜1200 の整数 |
| `*-seconds` | 0〜600 |
| `*-guard`・`*-cut` | 0〜1 |
| `*-share` | 0〜5 |
| `*-rate`・`*-bonus` | 0〜20 |
| `*-slow`・`*-might`・`*-protection`・`*-percent`・`*-pin` | 0〜100 |
| `*-radius`・`*-range`・`*-reach`・`*-length` | 0〜64 |
| そのほか | 0 (既定値が負ならその値) 〜 (100 か既定値の 10 倍の大きい方) |

- 次のものは使わず、警告を出します。プラグインは止まりません。警告は reload-report.txt とサーバーのログに出ます。
  - 範囲の外の数値、整数でない数
  - 知らない項目、ほかのクラスのコンボ
  - 読めないファイル
- 元の数値 (config.yml の値) は、そのまま残ります。
