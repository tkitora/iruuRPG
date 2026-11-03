# メニューの見た目 (menus/*.yml、t220)

ファストトラベルとダンジョンのメニューの見た目を、yml リポジトリの `menus/` で決めます。
- 決められるもの: 並び順、名前、名前の色、アイコン、説明の行、推奨レベル。
- ツールサイトの「メニュー」タブで編集します。
- 読み直しはほかのデータと同じです (起動・`/iruurpg reload`・yml の同期)。再起動は要りません。

見た目だけをここに置きます。次のものは、今までの場所のままです。
- ファストトラベルの地点がどこにあるか: ゲームの中で `/travel set` を使い、`travel/waypoints.yml` に入る。
- ダンジョンの中身: `dungeons/*.yml`。

ファイルが無いときや、書いていない項目は、今までどおりの見た目です (`gui/MenuLooks`)。

## menus/travel.yml
```yaml
title: ファストトラベル          # メニューの題
entries:                        # 地点の id ごと (travel/waypoints.yml の id)
  atras:
    order: 1                    # 並び順 (小さいほど先。書いていない地点は、そのあとに今までの順)
    name: アトラス村            # 名前 (書かなければ、地点の名前)
    color: aqua                 # 名前の色: ゲームの色の名前 (aqua、gold、green、dark_gray など) か #RRGGBB
    icon: ENDER_EYE             # アイコン (アイテムの名前)
    lore:                       # 名前の下の行 (距離・料金・「クリックで移動」の前)
      - 村の広場
unknown:                        # まだ見つけていない地点の見た目
  name: "???"
  color: dark_gray
  icon: GRAY_DYE
  lore: [まだ訪れていない]
```

## menus/dungeons.yml
```yaml
title: ダンジョン
entries:                        # ダンジョンの id ごと (dungeons/<id>.yml)
  cave:
    order: 1
    name: アトラスの洞窟
    color: green                # 書くと、入れないときも、この色
    icon: SPAWNER               # 書くと、入れないときも、このアイコン (準備中は灰色の染料のまま)
    lore: [アトラスの近くにある、小さな洞窟。]   # ダンジョンの説明の代わり
    recommended-level: 5        # 「推奨 LvN 以上」に出す数 (入れる条件は、dungeons の min-level のまま)
```

## 読めない値
- 知らない色・アイテム、整数でない order や recommended-level は、その項目だけ使いません。警告は reload-report.txt とサーバーのログに出ます。
- 読めないファイルは、まるごと使いません (今までの見た目)。
- プラグインは止まりません。
