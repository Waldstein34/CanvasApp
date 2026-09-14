# CanvasApp

[Studio](https://github.com/Waldstein34/Studio) のキャンバス機能を、PCと同じネットワーク（自宅Wi-Fiやテザリング）にいるスマホから操作するためのAndroidアプリ。

## 前提

- StudioのPC本体（`server.py`）が同じネットワーク上で起動していること
- Studioが持つキャンバスAPI（`/api/canvas`, `/api/canvas/op`, `/api/canvas/ai`, `/api/canvas/ai/apply` など）にHTTPで接続する

## 現状

段階1：Android Studioプロジェクトの土台のみ（Kotlin + Jetpack Compose）。PC接続・キャンバス表示・AI機能はこれから追加していく。

## 開発環境

- Android Studio
- minSdk 26 / targetSdk 35 / compileSdk 35
