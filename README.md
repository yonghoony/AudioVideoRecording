Android Camcorder
=========================

This is a camera library that records audio and video by using [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) and [MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer). It was a fork of https://github.com/saki4510t/AudioVideoRecordingSample.

- Being refactored and rewritten in Kotlin.
- `CameraGLView` contains the camera controls and preview rendering while `VideoRecorder` contains the recording logics.

### How to install
```
repositories {
    maven { url 'https://jitpack.io' }
}
```

```
dependencies {
    implementation 'com.github.yonghoony:Camcorder:v0.0.5'
}
```

### Demo App
```
./gradlew installDebug
```

### License
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

All files in the folder are under this Apache License, Version 2.0.
