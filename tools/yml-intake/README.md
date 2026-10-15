# yml 取り込み (Google フォーム → iruu_rpg_yml_file)

開発者が Google フォームで送った アイテム / モブ / レシピ の yml を、`iruuRPG/iruu_rpg_yml_file` の main に反映します。
フォームに制限は無く、検証に通ったものは全てそのまま反映します (承認なし)。

## フォームの項目 (「iruuRPG yml編集フォーム」)
| 項目 | 内容 |
|---|---|
| 新規ですか？ | 新規です / 既存の編集です |
| IDはなんですか？ | 半角の小文字・数字・`_`・`-` (64文字まで) |
| カテゴリを含んだymlをどうぞ | `items:` / `mobs:` / `recipes:` から書く。1つのカテゴリの下に、id を複数書いてもよい |

CSV の列は、見出しの先頭の言葉で探します。「種類」「操作」「メモ」「送信者(メールアドレス)」の列があれば、それも読みます (無くても動きます)。
カテゴリを書かずに id の中身だけを送ると、「種類」の列が無い限り、反映できません (理由を返します)。

## 動き
- 反映先: アイテム `items/<element>.yml` (element が無ければ `white.yml`)、モブ `mobs/dev.yml`、レシピ `crafting/recipes-dev.yml`。
- 編集は、その id が今ある場所のブロックを、**送られた内容に丸ごと置き換えます** (コメントや書いていない項目は消えます。編集では全項目を貼ってもらいます)。
- 反映できない行 (yml が読めない、id の形が違う、必須項目が無い) は、理由を結果の「開発者へ返す」に書きます。
- 管理者への通知: メモがある行、操作と実際が違う行、フォームの id と yml の中の id が合わない行 (新規なのに id がある / 編集なのに id が無い)。
- 反映後は、サーバーのプラグインが `yml-sync` で pull して読み直し、`reload-report.txt` に警告を書きます。

## 実行
```
tool-site/.venv/bin/python -I tools/yml-intake/intake.py --repo <yml リポジトリの作業ツリー> \
  --items-dir src/main/resources/items --csv <回答.csv> --state <state.json> --out <結果.md> --push
```
`--state` に処理済みの行を記録します (同じ行は二度反映しません)。`--push` は commit と main への push まで行います (iruuRPG アカウント)。
