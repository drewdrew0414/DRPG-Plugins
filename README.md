# levelSystem

Paper RPG level system plugin.

## Skript 연동

Skript 플러그인이 서버에 설치되어 있으면 levelSystem이 자동으로 Skript 문법을 등록합니다.

스킬 이름은 `plugins/RPG/levelSystem/jsons/*.json` 안의 `name` 값을 사용합니다.

### 값 조회

```vb
set {_level} to levelsystem level of player for "Mining"
set {_exp} to levelsystem exp of player for "Mining"
set {_required} to levelsystem required experience of player for "Mining"
set {_remaining} to levelsystem remaining experience of player for "Mining"
set {_progress} to levelsystem progress of player for "Mining"
set {_max} to levelsystem max level of player for "Mining"
```

반환값:

- `level`: 현재 레벨
- `exp`: 현재 레벨에서 보유 중인 EXP
- `required experience`: 다음 레벨까지 필요한 전체 EXP
- `remaining experience`: 다음 레벨까지 남은 EXP
- `progress`: 현재 레벨 진행률, `0`부터 `100`
- `max level`: JSON에 설정된 최대 레벨

### 스킬 이름 조회

```vb
set {_display} to levelsystem display name of "Mining"
set {_skillName} to levelsystem skill name of "Mining"
```

### EXP 지급

```vb
add 50 levelsystem experience to player for "Mining"
```

이 effect는 온라인 플레이어에게만 사용할 수 있습니다. 지급된 EXP는 기존 레벨업, 보상 지급, DB 저장 로직을 그대로 사용합니다.

### 예시

```vb
command /mininglevel:
    trigger:
        set {_level} to levelsystem level of player for "Mining"
        set {_exp} to levelsystem exp of player for "Mining"
        set {_required} to levelsystem required experience of player for "Mining"
        set {_progress} to levelsystem progress of player for "Mining"
        send "&aMining Lv.%{_level}% &7- &f%{_exp}%/%{_required}% EXP &7(%{_progress}%%%)" to player

command /giveminingexp <number>:
    permission: op
    trigger:
        add arg-1 levelsystem experience to player for "Mining"
        send "&aMining EXP를 지급했습니다." to player
```

첫 조회 시 아직 DB 캐시가 로드되지 않은 플레이어는 기본값이 반환될 수 있습니다. 조회가 들어오면 비동기로 DB 값을 캐싱하므로 이후 조회부터 저장된 값이 반환됩니다.
