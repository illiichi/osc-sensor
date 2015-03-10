1. java をインストール

2. android sdkをインストール

3. project.clj の下記の:sdk-path をandroid-sdkのパスにする

```
:android {;; Specify the path to the Android SDK directory.
            :sdk-path "/usr/local/Cellar/android-sdk/23.0.2/"
```

4. アンドロイド端末をUSB接続

5. ./lein droid doall
