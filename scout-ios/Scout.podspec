Pod::Spec.new do |s|
  s.name             = 'Scout'
  s.version          = '0.1.17'
  s.summary          = 'Scout RUM SDK for iOS (OpenTelemetry-based).'
  s.description       = 'Real User Monitoring for iOS: screens, HTTP, crashes, ANRs, vitals, ' \
                        'logs — a thin Swift layer (ScoutKit) over a shared Kotlin/Native engine.'
  s.homepage         = 'https://github.com/base-14/scout-kotlin-multiplatform'
  s.license          = { :type => 'Apache-2.0', :file => '../LICENSE' }
  s.author           = 'Base14'
  s.platform         = :ios, '13.0'
  s.swift_version    = '5.9'

  # ScoutKit Swift sources + the prebuilt Kotlin/Native engine (assemble first with
  # scripts/build-xcframework.sh). For a tagged release, set s.source to the release tag and
  # point vendored_frameworks at the packaged xcframework.
  s.source           = { :git => 'https://github.com/base-14/scout-kotlin-multiplatform.git', :tag => "ios-#{s.version}" }
  s.source_files     = 'scout-ios/ScoutKit/**/*.swift'
  s.vendored_frameworks = 'scout-ios/build/XCFrameworks/release/Scout.xcframework'

  s.dependency 'KSCrash', '2.5.1'
end
