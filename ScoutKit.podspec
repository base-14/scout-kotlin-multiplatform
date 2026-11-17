Pod::Spec.new do |s|
  s.name             = 'ScoutKit'
  # Kept equal to the ios-<version> tag. publish-ios.yml rewrites this line on release.
  s.version          = '0.1.13'
  s.summary          = 'Scout RUM SDK for iOS (OpenTelemetry-based).'
  s.description      = 'Real User Monitoring for iOS: screens, HTTP, crashes, ANRs, vitals, ' \
                       'logs. A thin Swift layer (ScoutKit) over the shared Kotlin/Native engine (Scout).'
  s.homepage         = 'https://github.com/base-14/scout-kotlin-multiplatform'
  s.license          = { :type => 'MIT', :file => 'LICENSE' }
  s.author           = 'Base14'
  s.platform         = :ios, '13.0'
  s.swift_version    = '5.9'

  s.source           = { :git => 'https://github.com/base-14/scout-kotlin-multiplatform.git',
                         :tag => "ios-#{s.version}" }

  # The Kotlin/Native engine is not in git. It is the Scout.xcframework.zip asset on the
  # ios-<version> GitHub Release, the same file Package.swift's binaryTarget uses. Download it
  # and check it against the checksum that publish-ios.yml pinned in Package.swift at this tag.
  s.prepare_command = <<-CMD
    set -e
    URL="https://github.com/base-14/scout-kotlin-multiplatform/releases/download/ios-#{s.version}/Scout.xcframework.zip"
    curl -fsSL -o Scout.xcframework.zip "$URL"
    EXPECTED=$(sed -n -E 's/.*checksum: "([a-f0-9]{64})".*/\\1/p' Package.swift)
    echo "$EXPECTED  Scout.xcframework.zip" | shasum -a 256 -c -
    unzip -q -o Scout.xcframework.zip
    rm Scout.xcframework.zip
  CMD

  s.source_files        = 'scout-ios/ScoutKit/**/*.swift'
  s.vendored_frameworks = 'Scout.xcframework'

  # Scout.xcframework ships ios-arm64 and ios-arm64-simulator only (no x86_64 simulator slice),
  # so never build the x86_64 simulator arch against it. Pod target only: a user_target_xcconfig
  # for this key collides with other pods that set it (CocoaPods then drops both values).
  s.pod_target_xcconfig = { 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'x86_64' }
end
