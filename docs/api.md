# 想定する API

城巡りアプリ（Castle Go）が castle-wiki の API に要望する仕様をまとめる．
対象読者は，アプリと wiki の API を実装する開発者と AI エージェント．
アプリ全体の方針は [企画案](proposal.md) に従う．

wiki 側に次の API を要望する．パス・名前は例であり，確定後に本書を更新すること．
アプリはデータの閲覧のみのため，すべて読み取り専用の `GET` とする．ログインは不要とする．

## エンドポイント一覧

| エンドポイント（例） | 役割 | 呼ぶタイミング |
| -------------------- | ---- | -------------- |
| `GET /sync/castles` | 一覧用の列の差分（削除を含む） | 初回・定期同期・手動更新 |
| `GET /sync/castle-tags` | タグ辞書と，城とタグの対応の差分 | 同上（城の同期と同時） |
| `GET /castles/{id}` | 最新版の本文・属性 | 詳細画面を開いたとき |
| `GET /photos/{id}/images/{size}` | 写真の WebP（既存の API．[写真の配信](#写真の配信)） | 一覧・詳細で表示するとき |

- 一覧・地図・車載のためだけに必要な API は `/sync/*` の2つとする
- 検索・絞り込み・距離順は端末で行うため，サーバー側の検索 API は不要

## 差分同期（城）

```text
GET /sync/castles?cursor=<カーソル>&limit=1000
```

| 項目 | 内容 |
| ---- | ---- |
| `cursor` | 省略すると先頭から．前回のレスポンスの `nextCursor` を渡す |
| `limit` | 1ページの件数．省略時と最大値はどちらも 1000．最大値を超える指定は 1000 に丸め，続きは `hasMore` で取得する |
| ソート | `updated_at`，`id` の昇順（カーソルと同じ並び） |
| 対象 | `updated_at` がカーソルより新しい城．**論理削除された城も含める** |

レスポンスの例（値は形式を示すためのもの）．

```json
{
  "castles": [
    {
      "id": "<城の ID>",
      "revision": 12,
      "name": "名古屋城",
      "nameKana": "なごやじょう",
      "searchText": "名古屋城 なごやじょう 金城",
      "lat": 35.185,
      "lng": 136.899,
      "scale": 6,
      "castleKind": "castle",
      "locationType": "hirajiro",
      "prefecture": "23",
      "tenshuStatus": "exterior_restoration",
      "meijoSelection": "top100",
      "meijoNumber": 46,
      "primaryPhotoId": "<写真の ID>",
      "updatedAt": 1763222400000,
      "deletedAt": null
    }
  ],
  "nextCursor": "<updated_at>_<id>",
  "hasMore": false,
  "serverTime": 1763300000000
}
```

| 項目 | 内容 |
| ---- | ---- |
| 列の元 | wiki の `castles` テーブルの写し列．JSON のキーは camelCase |
| 含めない列 | `is_protected`，`created_at`，`country`，`region` |
| 対象の国 | 日本（`country = 'JP'`）の城のみ．韓国の城は同期に含めない |
| 削除 | `deletedAt` が NULL でない行．端末は該当の城を削除済みとして扱う |
| 復元 | 同じ ID が `deletedAt: null` で再び届く |
| `hasMore` | `true` の間，`nextCursor` で続きを取得する |
| `serverTime` | 端末の時計に依存しないための基準．最終同期日時の表示に使う |

- 端末は，`hasMore` が `false` になった時点の `nextCursor` を保存し，次回の起点とする
- 差分がない場合は `castles` が空の配列となる．`ETag` に対応する場合は `304 Not Modified` を返す
- 同じ `updated_at` を持つ行が多数あっても取りこぼさないよう，カーソルは `updated_at` と `id` の組とする
- 取得の途中で通信が切れても，保存済みのカーソルから再開できる（冪等．同じ行を再度受け取っても上書きするだけ）

## 差分同期（タグ）

```text
GET /sync/castle-tags
```

| 項目 | 内容 |
| ---- | ---- |
| `tags` | `castle_tags` の全件（`id`，`name`）．件数が少ないため全件を返す |
| `assignments` | 城とタグの対応．`castleId`，`tagIds`（城ごとにまとめる） |

- タグはデータ量が小さいため，カーソルを使わず `ETag` で変更の有無を判定する
- 城の同期で更新された城の対応のみを返す方式（`?since=`）も，件数が増えた場合の選択肢とする

## 城の詳細

```text
GET /castles/{id}
If-None-Match: "<revision>"
```

| 項目 | 内容 |
| ---- | ---- |
| `revision` | 最新版の版番号．端末の詳細キャッシュとの比較に使う |
| `body` | 本文（Markdown） |
| `attributes` | 属性の JSON．各属性は `{ "state": "known", "value": <値> }`（値あり），`{ "state": "unknown" }`（不明）で表し，未入力はキーを持たない．年は `{ "year", "precision", "label" }` |
| `photos` | 城の写真の一覧（`id`，`caption`，`photographer`，`license`，`width`，`height`）．論理削除された写真は含めない |
| `ETag` | 版番号．`If-None-Match` が一致する場合は `304` を返し，本文を送らない |

- 論理削除された城は `404` ではなく `410 Gone` を返す（端末が削除と判断できる）
- 本文・属性・写真の一覧は1回のレスポンスにまとめ，詳細画面で追加の API を呼ばせない

## 写真の配信

wiki の既存の API をそのまま使う．

```text
GET /photos/{photoId}/images/{size}
```

| 項目 | 内容 |
| ---- | ---- |
| `size` | 長辺の大きさ．`400`，`1024`，`2048` のいずれか |
| 形式 | WebP |
| キャッシュ | `Cache-Control: public, max-age=86400, s-maxage=3600`，`ETag` あり（現在の実装） |
| 認証 | 不要 |
| 削除された写真 | `404` |

- 削除した写真を配信し続けないよう，キャッシュの期間は短い（現在の実装を優先し，変更しない）
- 端末は `ETag` を使い，期限後は `If-None-Match` で再検証する．変更がなければ `304` で本文を取り直さない
- 一覧・地図・車載では `400` を，詳細では `1024` を，全画面表示では `2048` を使う
- 代表写真の URL は `primaryPhotoId` から端末で組み立てる．取得用の API は設けない
- 削除された写真は `404` になるため，端末は代替の表示にする

## 共通の要件

| 項目 | 内容 |
| ---- | ---- |
| 認証 | 不要（[認証](#認証)を参照） |
| 圧縮 | `gzip` または `br` に対応する |
| レート制限 | 定期同期・詳細取得が制限に掛からないこと．超過時は `429` と `Retry-After` を返す |
| エラー | `4xx`・`5xx` は JSON（`code`，`message`）．端末は `5xx`・`429` で指数バックオフして再試行する |
| 版管理 | 破壊的な変更は `/v1/` のようにパスで版を分ける |
| 互換性 | レスポンスへのキーの追加は互換とする．端末は未知のキーを無視する |

## 認証

アプリのデータの取得（同期・詳細・写真）に，ログインは不要とする．wiki の閲覧 API は，現在も未ログインで使える．
アプリには，ログインの機能を設けない．訪問記録は端末のみに保存し，サーバーには送らない．

| 機能 | ログイン |
| ---- | -------- |
| 一覧・検索・絞り込み・地図・周辺の城 | 不要 |
| 差分同期（`/sync/*`） | 不要 |
| 城の詳細 | 不要 |
| 写真の表示 | 不要 |
| Android Auto | 不要 |
| 訪問記録 | 不要（端末のみ） |

- 同梱スナップショットも，ログインなしで使える
- 通信には，利用者の情報（現在地・訪問記録）を含めない
- 新しい `/sync/*` は，既存の閲覧 API と同じく，認証を求めない

### 将来の検討：ログインが必要になった場合

訪問記録のアカウントへの同期など，利用者の情報をサーバーに置く機能を足す場合のための方針．MVP では実装しない．

#### 方式

| 項目 | 内容 |
| ---- | ---- |
| ログイン | メールのワンタイムパスワード，または passkey（Credential Manager）．ログインを促すのは，最新の取得・詳細を開くときとする |
| トークン | 推測できない乱数の不透明なトークンを1つ発行する．API には `Authorization: Bearer <トークン>` で渡す |
| 発行 | ログインの検証 API が，Cookie の代わりにトークンを JSON で返す．wiki の既存のログイン処理を再利用する |
| サーバーの保存 | wiki の `sessions` と同じ仕組みで，ハッシュ値のみを保存する．種別（アプリ用）と端末の名前を追加する |
| 権限 | 読み取り専用．アプリ用のトークンは `GET` の `/sync/*`・`/castles/{id}` のみに使え，編集の API には使えない（盗難時の被害を抑える） |
| 有効期限 | 90 日（例）．利用のたびに延長する．ただし延長の書き込みは 1 日に 1 回までとし，D1 の書き込みを抑える |
| 失効 | 利用者がログアウトしたとき，または wiki のセッション管理画面で端末を指定して削除したとき |
| 端末での保存 | Android Keystore の鍵で暗号化して DataStore に保存する |
| `401` のとき | 背景の同期を止め，アプリを開いたときにログインを促す．保存済みのデータは引き続き表示できる |
| 利用者情報 | 通信には，認証トークン以外の利用者の情報（現在地・訪問記録）を含めない |

- 有効期限を長くするのは，毎日開かないアプリでも背景の同期が失効しないようにするため
- 短い有効期限のアクセストークンと更新トークンの二段構えは，読み取り専用・権限の限定・端末単位の失効で被害を抑えられるため，採らない
- passkey は，wiki のドメインを RP ID とする．Android アプリから使うには，wiki のドメインに `/.well-known/assetlinks.json`（アプリのパッケージ名と署名証明書のハッシュ値）を置く必要がある
- 編集を停止された利用者（`suspended_at`）も，閲覧はできる

#### ログイン API

wiki の現在の実装（`castle-wiki/api/src/features/auth`）を基準とする．パスは `/api` の下．

| エンドポイント | リクエスト | レスポンス | 備考 |
| -------------- | ---------- | ---------- | ---- |
| `POST /api/auth/one-time-password` | `{ "email" }` | `204` | 同じメールアドレスへは 1 分に 1 回まで．短い間隔の要求は送信せずに `204` を返す．メールの送信に失敗した場合は `500` |
| `POST /api/auth/one-time-password/verify` | `{ "email", "code" }`（`code` は 6 桁の数字） | `200` と利用者（`Me`） | 成功すると Cookie `castle_wiki_session` を設定する．`400`（誤り・期限切れ・誤りが 5 回），`404`（コードは正しいがメールアドレスが未登録．コードは使用済みになる） |
| `POST /api/auth/sign-up` | `{ "email", "code", "name" }` | `201` と `Me` | 新規登録．ログインとは別の API．アプリは新規登録を扱わない（下記） |
| `POST /api/auth/passkey/options` | なし | WebAuthn の options | challenge を署名付き Cookie（5 分）に保存する |
| `POST /api/auth/passkey/verify` | `{ "response" }` | `200` と `Me` | challenge の Cookie を使って検証し，セッションの Cookie を設定する．`400` |
| `POST /api/auth/sign-out` | なし | `204` | セッションを削除する．未ログインでも `204` |

`Me` の形は `{ "id", "email", "name", "role", "isSuspended" }`．

| 項目 | 現在の実装 |
| ---- | ---------- |
| ワンタイムパスワード | 6 桁，有効期限 10 分，誤りは 5 回まで，再送は 1 分に 1 回 |
| セッション | Cookie `castle_wiki_session`（`HttpOnly`，`Secure`，`SameSite=Lax`）．DB にはハッシュ値のみ保存．有効期限は 7 日で，リクエストのたびには延ばさない．最後のリクエストの日時は 1 時間に 1 回まで更新する |
| passkey の challenge | 署名付き Cookie `castle_wiki_webauthn_challenge`（`SameSite=Strict`，パス `/api`，5 分） |
| passkey の RP ID・origin | 環境変数 `WEBAUTHN_RP_ID`，`WEBAUTHN_ORIGIN`（Web のオリジン 1 つ）で検証する |
| CSRF | `csrf` ミドルウェアが，Web のオリジン以外からのフォームの送信を拒否する．JSON のリクエストは対象外 |
| 認証が必要な API | 城・写真・タグの閲覧（`GET`）は未ログインでも使える．編集系は `requireEditor`，`requireAdmin` |
| ログイン | 未登録のメールアドレスでは，ログインも利用者の作成もしない（`404`）．新規登録は `sign-up` で別に行う |

##### アプリで使うには（現在の実装との差）

そのままではアプリで使えない点と，wiki 側に必要な変更．

| 項目 | 現在の実装 | アプリ向けの変更 |
| ---- | ---------- | ---------------- |
| トークンの渡し方 | Cookie のみ | `Authorization: Bearer <トークン>` に対応する．`resolveSessionUser` で Cookie がない場合に Bearer を見る |
| トークンの発行 | Cookie に設定する | リクエストに `client: "app"` を指定した場合は，Cookie の代わりに `{ "token", "expiresAt" }` を JSON で返す（OTP・passkey の両方） |
| passkey の challenge | 署名付き Cookie | `client: "app"` の場合は，署名した短い有効期限（5 分）の値 `challengeToken` を `options` のレスポンスで返し，`verify` のリクエストで受け取る |
| passkey の origin | `WEBAUTHN_ORIGIN` の 1 つ | Android の origin（`android:apk-key-hash:<署名証明書のハッシュ値>`）も許可する．`expectedOrigin` に配列で渡す |
| 有効期限 | 7 日．延長なし | アプリ用のトークンは 90 日（例）．利用で延長するが，延長の書き込みは 1 日に 1 回まで |
| 種別 | なし | `sessions` にアプリ用の種別と端末の名前（`deviceName`）を追加する |
| 権限 | 全 API | アプリ用のトークンは，`GET` の `api/castles/*`・`/api/castle-tags`・`/sync/*` などの閲覧のみ．編集系は拒否する |
| 端末の削除 | なし | セッション管理画面に，端末名での一覧と削除を追加する |

- 新規登録（`sign-up`）は，表示名の入力・不適切な表示名の判定があるため，アプリでは扱わない．未登録のメールアドレス（`404`）の場合は，Web の新規登録へ案内する
- アプリが使うのは，`one-time-password`，`one-time-password/verify`，`passkey/options`，`passkey/verify`，`sign-out` の 5 つ

## 端末側の対応

| 状況 | 端末の動作 |
| ---- | ---------- |
| 差分が空・`304` | 何もしない．最終確認日時のみ更新する |
| 同期の途中で失敗 | 保存済みのカーソルから次回再開する |
| 詳細が `410` | 該当の城を削除済みにし，一覧から外す |
| 詳細が `304` | キャッシュをそのまま使う |
| 同期が長期間ない | 端末のカーソルが古くても差分で追従できる．サーバーはカーソルの有効期限を設けない |
| 取得が必要な列が増えた | アプリの更新時に，Room のマイグレーションとカーソルの初期化（全件の再取得）を行う |

## wiki 側の作業

| 作業 | 内容 |
| ---- | ---- |
| 同期の API | `/sync/castles`，`/sync/castle-tags`（日本の城のみ，削除を含む，認証不要） |
| 索引 | `castles` に `updated_at`，`id` の索引を追加する |
| 詳細の API | `/castles/{id}`（`ETag`，`410`）．既存の `GET /castles/{castleId}` で足りるかを確認し，足りなければ拡張する |

写真の配信とログインは，wiki 側の変更が不要．
