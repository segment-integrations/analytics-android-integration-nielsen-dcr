analytics-android-integration-nielsen-dcr
======================================
[![CircleCI](https://circleci.com/gh/segment-integrations/analytics-android-integration-nielsen-dcr.svg?style=svg)](https://circleci.com/gh/segment-integrations/analytics-android-integration-nielsen-dcr)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.segment.analytics.android.integrations/nielsendcr/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.segment.analytics.android.integrations/nielsendcr)
[![Javadocs](http://javadoc-badge.appspot.com/com.segment.analytics.android.integrations/nielsendcr.svg?label=javadoc)](http://javadoc-badge.appspot.com/com.segment.analytics.android.integrations/nielsendcr)

Nielsen DCR integration for [analytics-android](https://github.com/segmentio/analytics-android).


## Installation
Nielsen App SDK is compatible with Android OS versions 2.3+

To install the Segment-Nielsen-SCR integration, add the following to your gradle file:

```
compile 'com.segment.analytics.android.integrations:nielsendcr:+'
```

Nielsen does not host their SDK on Maven, so you must manually include their framework after downloading it from the [Nielsen site](https://engineeringforum.nielsen.com/sdk/developers/download-sdk-2.php).

## Usage

After adding the dependency, you must register the integration with our SDK.  To do this, import the Nielsen DCR integration:


```
import com.segment.analytics.android.integrations.nielsendcr.NielsenDCRIntegration;

```

And add the following line:

```
 analytics = new Analytics.Builder(this, "write_key")
                .use(NielsenDCRIntegration.FACTORY)
                .build();
```

Please see [our documentation](https://segment.com/docs/integrations/nielsne-dcr/#mobile) for more information.

## License

```
WWWWWW||WWWWWW
 W W W||W W W
      ||
    ( OO )__________
     /  |           \
    /o o|    MIT     \
    \___/||_||__||_|| *
         || ||  || ||
        _||_|| _||_||
       (__|__|(__|__|

The MIT License (MIT)

Copyright (c) 2017 Segment, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
