#!/bin/sh
set -eu

root=${1:?secret root is required}
generation=${2:?generation id is required}
shift 2

case "$root" in
  /run/auto-parto|/run/auto-parto-db|/run/auto-parto-audit|/run/auto-parto-object-storage|/run/auto-parto-migration) ;;
  *) exit 2 ;;
esac
case "$generation" in
  ''|*[!a-f0-9]*) exit 2 ;;
esac
test "${#generation}" -eq 64
test "$#" -gt 0
generation_root="$root/generations/$generation"
test -d "$generation_root"

for relative_path do
  case "$root:$relative_path" in
    /run/auto-parto:secrets/secrets.properties|/run/auto-parto:audit/secrets.properties|/run/auto-parto-db:database/bootstrap.properties|/run/auto-parto-db:backup/secrets.properties|/run/auto-parto-audit:database/audit-admin.properties|/run/auto-parto-object-storage:object-storage/admin.properties|/run/auto-parto-migration:migration/secrets.properties) ;;
    *) exit 2 ;;
  esac
  test -f "$generation_root/$relative_path"
  test ! -L "$generation_root/$relative_path"
done

temporary_link="$root/active.next"
rm -f -- "$temporary_link"
ln -s "generations/$generation" "$temporary_link"
mv -Tf -- "$temporary_link" "$root/active"
