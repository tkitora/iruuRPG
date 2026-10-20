#!/usr/bin/env python3
"""Google フォームの回答 (CSV) を iruu_rpg_yml_file に反映する。

使い方:
  intake.py --repo <yml リポジトリの作業ツリー> --items-dir <プラグインの resources/items> \
            --csv <回答.csv> [--state <処理済みの記録.json>] [--out <結果.md>] [--push]

CSV の列 (見出しの名前で読む。順番は自由):
  タイムスタンプ / 送信者 / 種類 (アイテム|モブ|レシピ) / 操作 (新規|編集) / id / yml / メモ

- フォームには制限をかけない。内容が検証 (YAML として読める、id の形、必須項目) を通れば、そのまま反映する。
- 検証に通らない行は、反映せずに、理由を結果に書く (開発者に返す文面)。
- 「メモが書かれている」「操作と実際が違う (新規なのに id が既にある / 編集なのに id が無い)」行は、
  反映したうえで、結果の「要確認」に入れる (管理者に知らせる対象)。
- 反映先: アイテム items/<属性>.yml (属性が無ければ white) / モブ mobs/dev.yml / レシピ crafting/recipes-dev.yml。
  編集は、その id が今ある場所のブロックを置き換える。
"""
import argparse
import csv
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

import yaml

KINDS = {"アイテム": "items", "item": "items", "items": "items",
         "モブ": "mobs", "mob": "mobs", "mobs": "mobs",
         "レシピ": "recipes", "recipe": "recipes", "recipes": "recipes"}
ID_RE = re.compile(r"^[a-z0-9][a-z0-9_\-]{0,63}$")
DAMAGE_KINDS = ("NONE", "PHYSICAL", "MAGIC", "SPECIAL", "MATERIAL", "ARMOR")
CLASS_IDS = ("warrior", "mage", "gifted", "healer", "assassin")
CLASS_ALIASES = {**{c: c for c in CLASS_IDS}, "ranger": "gifted", "cleric": "healer", "戦士": "warrior", "メイジ": "mage",
                 "レンジャー": "gifted", "クレリック": "healer", "アサシン": "assassin"}
CLASS_STATES = ("favorite", "usable", "unusable")
CLASS_STATE_JA = ("得意", "使用可", "使用不可")
ELEMENT_FILES = {"RED": "red", "BLUE": "blue", "GREEN": "green", "ORANGE": "orange", "WHITE": "white"}
MAX_YML = 20000


def col(row, *names):
    for name in names:
        for key, value in row.items():
            if key and key.strip() == name:
                return (value or "").strip()
    return ""


def colp(row, *prefixes):
    """The value of the first column whose header starts with one of the prefixes."""
    for prefix in prefixes:
        for key, value in row.items():
            if key and key.strip().startswith(prefix):
                return (value or "").strip()
    return ""


def split_top(text):
    """Top-level 'key:' blocks of a file: (header_lines, {id: (start, end)}) with ids at 2-space indent."""
    lines = text.split("\n")
    spans, current = {}, None
    for index, line in enumerate(lines):
        m = re.match(r"^  ([^\s#:][^:]*):\s*(#.*)?$", line)
        if m:
            if current:
                spans[current[0]] = (current[1], index)
            current = (m.group(1).strip().strip('"\''), index)
        elif current and line and not line.startswith(" ") and not line.startswith("#"):
            spans[current[0]] = (current[1], index)
            current = None
    if current:
        spans[current[0]] = (current[1], len(lines))
    # trim trailing blank lines from each span
    for key, (start, end) in list(spans.items()):
        while end > start + 1 and not lines[end - 1].strip():
            end -= 1
        spans[key] = (start, end)
    return lines, spans


def render_block(entry_id, body):
    dumped = yaml.safe_dump(body, allow_unicode=True, sort_keys=False, default_flow_style=False).rstrip("\n")
    return ["  %s:" % entry_id] + ["    " + line if line else "" for line in dumped.split("\n")]


def parse_entries(text, entry_id, kind):
    """The pasted yml normally starts with the category ('items:' / 'mobs:' / 'recipes:') and holds one or more ids.
    Without a category, the kind comes from the form's kind column and the text is 'id:\\n  fields' or just the fields.
    Returns (kind, [(id, body), ...])."""
    data = yaml.safe_load(text)
    if not isinstance(data, dict) or not data:
        raise ValueError("yml の中身が読めません。`mobs:` / `items:` / `recipes:` から、キー: 値の形で書いてください (受け取った文字: %r)" % text[:30])
    for top in ("items", "mobs", "recipes"):
        if top in data:
            if len(data) != 1 or not isinstance(data[top], dict) or not data[top]:
                raise ValueError("%s: の下に、id ごとの中身を書いてください(カテゴリは1つだけ)" % top)
            entries = []
            for key, value in data[top].items():
                if not isinstance(value, dict):
                    raise ValueError("%s の中身がマップ(キー: 値)になっていません" % key)
                entries.append((str(key), value))
            return top, entries
    if not kind:
        raise ValueError("カテゴリ(items: / mobs: / recipes:)から書いてください")
    if len(data) == 1:
        (only_key, only_value), = data.items()
        if isinstance(only_value, dict) and (only_key == entry_id or not entry_id):
            return kind, [(str(only_key), only_value)]
    return kind, [(entry_id, data)]


class Repo:
    def __init__(self, root, items_dir, adopt_dir=None):
        self.root = Path(root)
        self.items_dir = Path(items_dir) if items_dir else None
        # the server's working copy (items/ mobs/ crafting/ as the plugin reads them): a file the yml repository does not have yet
        # (a bundled file such as mobs/dungeon.yml) is taken over into the repository when an entry in it is edited
        self.adopt_dir = Path(adopt_dir) if adopt_dir else None
        self.adopted = []

    def file(self, kind, element=None):
        if kind == "items":
            return self.root / "items" / ("%s.yml" % ELEMENT_FILES.get(str(element).upper(), "white"))
        if kind == "mobs":
            return self.root / "mobs" / "dev.yml"
        return self.root / "crafting" / "recipes-dev.yml"

    def files(self, kind):
        folder = {"items": "items", "mobs": "mobs", "recipes": "crafting"}[kind]
        return sorted((self.root / folder).glob("*.yml")) if (self.root / folder).exists() else []

    def locate(self, kind, entry_id):
        top = kind
        for path in self.files(kind):
            lines, spans = split_top(path.read_text(encoding="utf-8"))
            if entry_id in spans and top in "\n".join(lines):
                return path
        if self.adopt_dir:
            folder = {"items": "items", "mobs": "mobs", "recipes": "crafting"}[kind]
            for path in sorted((self.adopt_dir / folder).glob("*.yml")) if (self.adopt_dir / folder).exists() else []:
                lines, spans = split_top(path.read_text(encoding="utf-8"))
                if entry_id in spans:
                    target = self.root / folder / path.name
                    if not target.exists():
                        target.parent.mkdir(parents=True, exist_ok=True)
                        target.write_text(path.read_text(encoding="utf-8"), encoding="utf-8")
                        self.adopted.append(str(target.relative_to(self.root)))
                    return target
        return None

    def known_item_ids(self):
        ids = set()
        folders = [self.root / "items"]
        if self.items_dir:
            folders.append(self.items_dir)
        for folder in folders:
            for path in folder.glob("*.yml") if folder.exists() else []:
                data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
                ids.update((data.get("items") or {}).keys())
        return ids


def validate(kind, entry_id, body, repo):
    problems, warnings = [], []
    if not ID_RE.match(entry_id or ""):
        problems.append("id は半角の小文字・数字・_ - だけで、64文字までです: %r" % entry_id)
    if kind == "items":
        if not body.get("material"):
            problems.append("material (素材名) がありません")
        if not body.get("name"):
            problems.append("name (表示名) がありません")
        # 説明文の1行目 (Lv N 種類 攻撃タイプ) の表記: kind-label / type-label は、任意の文字 (空欄 = 自動)
        for key in ("kind-label", "type-label"):
            if body.get(key) is not None and not isinstance(body.get(key), str):
                problems.append("%s は文字です (空欄にすると、自動の表記になります)" % key)
        # classes: クラスごとの 得意 / 使用可 / 使用不可 (間違いは、プラグインが読み飛ばすので、警告)
        classes = body.get("classes")
        if classes is not None:
            if not isinstance(classes, dict):
                problems.append("classes は、クラス: 状態 のマップです (例: warrior: favorite)")
            else:
                seen = {}
                for key, value in classes.items():
                    cid = CLASS_ALIASES.get(str(key).strip().lower()) or CLASS_ALIASES.get(str(key).strip())
                    if not cid:
                        warnings.append("classes.%s: 知らないクラスです (%s、または 戦士 メイジ レンジャー クレリック アサシン)" % (key, " / ".join(CLASS_IDS)))
                        continue
                    if str(value).strip().lower() not in CLASS_STATES and str(value).strip() not in CLASS_STATE_JA:
                        warnings.append("classes.%s: '%s' は、favorite / usable / unusable (得意 / 使用可 / 使用不可) ではありません" % (key, value))
                    if cid in seen:
                        warnings.append("classes: 同じクラスが2回書かれています (%s と %s)" % (seen[cid], key))
                    seen[cid] = key
                if str(body.get("attack-type", "")).upper() == "SPECIAL":
                    warnings.append("classes: attack-type が SPECIAL の武器では、無視されます (マークの行も制限もありません)")
        kind_value = body.get("damage-kind")
        if kind_value is not None and str(kind_value).upper() not in DAMAGE_KINDS:
            warnings.append("damage-kind '%s' は、%s のどれかではありません" % (kind_value, " / ".join(DAMAGE_KINDS)))
    elif kind == "mobs":
        if not body.get("type"):
            problems.append("type (モブの種類) がありません")
        area = body.get("area")
        if area is not None and not (isinstance(area, str) or (isinstance(area, list) and all(isinstance(a, str) for a in area))):
            problems.append("area は、文字列か、文字列のリスト (例: area: [cave, field1]) で書いてください")
    else:
        result = body.get("result")
        ingredients = body.get("ingredients")
        if not result:
            problems.append("result (完成品のアイテム id) がありません")
        if not isinstance(ingredients, dict) or not ingredients:
            problems.append("ingredients (素材のアイテム id: 数) がありません")
        else:
            known = repo.known_item_ids()
            for item in [result] + list(ingredients):
                if item and known and item not in known:
                    warnings.append("アイテム id '%s' が見つかりません (これから追加するなら問題ありません)" % item)
    return problems, warnings


def drop_nulls(value):
    """Keys written with no value ("description:" and nothing after it) mean 'not set': leave them out instead of writing 'null'."""
    if isinstance(value, dict):
        return {k: drop_nulls(v) for k, v in value.items() if v is not None}
    if isinstance(value, list):
        return [drop_nulls(v) for v in value]
    return value


def apply(repo, kind, entry_id, mode, body):
    """Writes the entry. Returns (path, existed_before)."""
    body = drop_nulls(body)
    existing = repo.locate(kind, entry_id)
    element = body.get("element") if kind == "items" else None
    path = existing or repo.file(kind, element)
    top = kind if kind != "recipes" else "recipes"
    block = render_block(entry_id, body)
    if path.exists():
        lines, spans = split_top(path.read_text(encoding="utf-8"))
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        lines, spans = [top + ":"], {}
    if entry_id in spans:
        start, end = spans[entry_id]
        lines[start:end] = block
    else:
        if top + ":" not in [l.rstrip() for l in lines]:
            lines = [top + ":"] + lines
        while lines and not lines[-1].strip():
            lines.pop()
        lines += ([""] if len(lines) > 1 else []) + block
    path.write_text("\n".join(lines).rstrip("\n") + "\n", encoding="utf-8")
    return path, existing is not None


def git(repo_root, *args, check=True):
    return subprocess.run(["git", "-C", str(repo_root)] + list(args), check=check, capture_output=True, text=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True)
    ap.add_argument("--items-dir")
    ap.add_argument("--adopt-from", help="サーバーの plugins/iruuRPG のコピー。yml リポジトリに無い配布ファイルの中のエントリを編集したとき、そのファイルをリポジトリに取り込む")
    ap.add_argument("--csv", required=True)
    ap.add_argument("--state", help="処理済みの行を覚えておくファイル (json)")
    ap.add_argument("--out", help="結果を書く markdown")
    ap.add_argument("--push", action="store_true", help="git commit と push まで行う")
    args = ap.parse_args()

    repo = Repo(args.repo, args.items_dir, args.adopt_from)
    state_path = Path(args.state) if args.state else None
    done = set(json.loads(state_path.read_text())) if state_path and state_path.exists() else set()

    ok, failed, review = [], [], []
    with open(args.csv, encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    for number, row in enumerate(rows, start=2):
        raw = "|".join((v or "") for v in row.values())
        key = hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]
        if key in done:
            continue
        who = col(row, "送信者", "メールアドレス", "メール") or "(不明)"
        label = "行%d (%s)" % (number, who)
        kind_text = col(row, "種類")
        kind = KINDS.get(kind_text) or KINDS.get(kind_text.lower())
        given_id = col(row, "id", "ID", "Id") or colp(row, "ID")
        mode_text = col(row, "操作") or colp(row, "新規ですか")
        mode = "edit" if "編集" in mode_text or "edit" in mode_text.lower() else "new" if "新規" in mode_text else ""
        text = col(row, "yml", "YML") or colp(row, "カテゴリを含んだyml", "yml", "YML")
        memo = col(row, "メモ", "備考") or colp(row, "メモ")
        if len(text) > MAX_YML:
            failed.append((label, given_id, "yml が長すぎます (%d 文字まで)" % MAX_YML))
            done.add(key)
            continue
        try:
            kind, entries = parse_entries(text, given_id, kind)
        except Exception as exc:  # yaml.YAMLError and ValueError
            failed.append((label, given_id, "yml を読めません: %s" % str(exc).replace("\n", " ")))
            done.add(key)
            continue
        extra = []
        if given_id and given_id not in [i for i, _ in entries]:
            extra.append("フォームの id『%s』が、yml の中の id (%s) にありません" % (given_id, ", ".join(i for i, _ in entries)))
        for entry_id, body in entries:
            problems, warnings = validate(kind, entry_id, body, repo)
            if problems:
                failed.append((label, entry_id, " / ".join(problems)))
                continue
            path, existed = apply(repo, kind, entry_id, mode, body)
            where = str(path.relative_to(repo.root))
            ok.append((label, kind, entry_id, where, warnings))
            notes = list(extra)
            if memo:
                notes.append("メモ「%s」" % memo)
            if mode == "new" and existed:
                notes.append("『新規』でしたが、id が既にあったので上書きしました")
            if mode == "edit" and not existed:
                notes.append("『編集』でしたが、id が無かったので新規に追加しました")
            if notes:
                review.append((label, kind, entry_id, where, notes))
        done.add(key)

    out = ["# yml 取り込みの結果", ""]
    out += ["## 反映した (%d)" % len(ok)] + ["- %s: %s `%s` → %s%s" % (l, k, i, w, ("  ⚠ " + " / ".join(ws)) if ws else "")
                                           for l, k, i, w, ws in ok] + [""]
    out += ["## 反映できなかった (%d) — 開発者へ返す" % len(failed)] + ["- %s `%s`: %s" % f for f in failed] + [""]
    out += ["## 要確認 (%d) — 管理者へ" % len(review)] + ["- %s %s `%s` (%s): %s" % (l, k, i, w, " / ".join(n))
                                                      for l, k, i, w, n in review] + [""]
    if repo.adopted:
        out += ["## yml リポジトリに取り込んだファイル (配布ファイルの編集)"] + ["- %s" % a for a in repo.adopted] + [""]
    report = "\n".join(out)
    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
    print(report)

    if ok and args.push:
        git(args.repo, "add", "-A")
        message = "form: %d entries (%s)" % (len(ok), ", ".join("%s/%s" % (k, i) for _, k, i, _, _ in ok)[:200])
        git(args.repo, "commit", "-m", message)
        git(args.repo, "push", "origin", "HEAD:main")
    if state_path:
        state_path.write_text(json.dumps(sorted(done)))
    return 0 if not failed else 2


if __name__ == "__main__":
    sys.exit(main())
