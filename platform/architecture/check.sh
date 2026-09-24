#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$repo_root"

failed=0

require_path() {
    if [ ! -e "$1" ]; then
        echo "missing required paved-road path: $1" >&2
        failed=1
    fi
}

while IFS= read -r component; do
    case "$component" in
        ''|'#'*) continue ;;
    esac
    require_path "$component"
    case "$component" in
        services/*)
            if ! rg -q -F "includeBuild('$component')" settings.gradle; then
                echo "modern service is not included in the root Gradle lifecycle: $component" >&2
                failed=1
            fi
            ;;
        web/*)
            for lifecycle in build test; do
                require_path "$component/$lifecycle.sh"
                if ! rg -q -F "file('$component/$lifecycle.sh')" build.gradle; then
                    echo "modern web component is not included in root Gradle $lifecycle: $component" >&2
                    failed=1
                fi
            done
            ;;
    esac
done < platform/components.txt

for path in platform/contracts platform/ownership.yaml local-dev/docker-compose.yml; do
    require_path "$path"
done

if rg -n --glob 'services/**/build.gradle*' \
    --glob 'services/**/settings.gradle*' \
    '(mavenCentral|gradlePluginPortal|repo1\.maven|plugins\.gradle\.org)' services; then
    echo 'service builds must resolve exclusively through the injected approved repository' >&2
    failed=1
fi

if rg -n --glob 'services/**/build.gradle*' \
    '(applications/|framework/|themes/|project.*(applications|framework|themes))' services; then
    echo 'modern services must not depend on OFBiz code' >&2
    failed=1
fi

if rg -n -P --glob 'services/**/src/main/**' \
    '(jdbc:.*ofbiz|runtime/data|applications/datamodel|import org\.apache\.ofbiz\.(?!modern\.))' services; then
    echo 'modern services must not access OFBiz data or libraries directly' >&2
    failed=1
fi

if rg -n --glob '!platform/architecture/check.sh' \
    '(latest\.release|:latest|:[+])' \
    services platform web local-dev; then
    echo 'paved-road dependencies and images must not use floating versions' >&2
    failed=1
fi

exit "$failed"
