.DEFAULT_GOAL := help
GRADLE := ./gradlew

.PHONY: help ci fmt fmt-check lint test build clean

help: ## List available targets
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

ci: fmt-check lint test build ## Full CI: format check + lint + tests + build

fmt: ## Auto-format all Kotlin (Spotless + ktlint)
	$(GRADLE) spotlessApply

fmt-check: ## Verify formatting (fails if unformatted)
	$(GRADLE) spotlessCheck

lint: ## Static analysis (Android lint)
	$(GRADLE) :scout-android:lintDebug

test: ## Unit + conformance tests (KMP jvm target)
	$(GRADLE) :scout-core:jvmTest

build: ## Assemble the library AAR + core jar
	$(GRADLE) :scout-android:assembleDebug :scout-core:jvmJar

clean: ## Remove build outputs
	$(GRADLE) clean
