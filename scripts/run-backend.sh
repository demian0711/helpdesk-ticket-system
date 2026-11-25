#!/usr/bin/env bash
set -eo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$repo_root"

if [[ ! -f .env ]]; then
    printf 'Thiếu tệp .env tại %s. Hãy sao chép .env.example thành .env và điền cấu hình local.\n' "$repo_root" >&2
    exit 1
fi

# Bỏ ký tự CR cuối dòng để Git Bash nạp được cả tệp .env dùng CRLF.
set -a
source <(sed 's/\r$//' .env)
set +a

backend_port=${SERVER_PORT:-8080}
if [[ ! $backend_port =~ ^[1-9][0-9]{0,4}$ ]] || (( backend_port > 65535 )); then
    printf 'SERVER_PORT phải là số nguyên từ 1 đến 65535.\n' >&2
    exit 1
fi

is_helpdesk() {
    local response status body state
    local whitespace=$'[ \t\r\n]*'
    if ! response=$(curl -q --silent --noproxy '*' --max-time 2 \
        --write-out $'\n%{http_code}' "http://localhost:$backend_port/api/health" 2>/dev/null); then
        return 1
    fi

    status=${response##*$'\n'}
    body=${response%$'\n'*}
    case "$status" in
        200) state=UP ;;
        503) state=DOWN ;;
        *) return 1 ;;
    esac

    # Chấp nhận đúng hai trường JSON của Helpdesk theo bất kỳ thứ tự nào.
    local status_field="\"status\"${whitespace}:${whitespace}\"$state\""
    local database_field="\"database\"${whitespace}:${whitespace}\"$state\""
    local fields="(${status_field}${whitespace},${whitespace}${database_field}|${database_field}${whitespace},${whitespace}${status_field})"
    local health_json="^${whitespace}\{${whitespace}${fields}${whitespace}\}${whitespace}$"
    [[ $body =~ $health_json ]]
}

if ! connections=$(netstat -ano); then
    printf 'Không kiểm tra được cổng backend %s bằng netstat.\n' "$backend_port" >&2
    exit 1
fi

# Chỉ đọc cột địa chỉ local để tránh nhầm cổng ở cột địa chỉ từ xa.
pids=$(awk -v port="$backend_port" '
    $1 == "TCP" && $2 ~ (":" port "$") && $4 == "LISTENING" {
        sub(/\r$/, "", $5)
        if ($5 ~ /^[0-9]+$/ && !seen[$5]++) {
            printf "%s%s", separator, $5
            separator = ", "
        }
    }
' <<< "$connections")

if [[ -n $pids ]]; then
    printf 'Cổng backend %s đang được sử dụng (PID %s).\n' "$backend_port" "$pids" >&2
    if is_helpdesk; then
        printf 'Helpdesk đang phản hồi tại http://localhost:%s/api/health. Hãy dùng bản đang chạy hoặc dừng nó trước khi chạy lại script.\n' "$backend_port" >&2
    fi
    exit 1
fi

cd -- backend
exec bash ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"
