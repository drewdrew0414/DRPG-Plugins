# storageGuild

Paper 서버용 개인/길드 창고 플러그인입니다. DBManager의 SQLite API를 사용해서 창고 아이템 전체 데이터를 저장하고, 길드/초대권/확장권/페이지 창고를 제공합니다.

## 필요 플러그인

- 필수: `DBManager-1.0.0.jar`
- 선택: `Skript`, `PlaceholderAPI`

`storageGuild-1.0.0.jar`와 `DBManager-1.0.0.jar`를 서버 `plugins` 폴더에 같이 넣어야 합니다. Skript/PAPI는 있으면 자동으로 연동되고, 없으면 기본 명령어 기능만 동작합니다.

## 빌드

```powershell
.\gradlew.bat build
```

빌드 결과:

```text
build/libs/storageGuild-1.0.0.jar
```

## 창고 기능

- 개인 창고: `/storage`
- 개인 창고 특정 페이지: `/storage personal <페이지>`
- 길드 창고: `/storage guild`
- 특정 길드 창고: `/storage guild <길드> [페이지]`
- 창고 확장: `/storage expand [길드]`
- 창고 확장권 지급: `/storage ticket expansion [수량]`

창고 GUI는 기본 9x6입니다. 하단 1줄은 페이지 버튼/정보 영역이고, 아이템 칸은 페이지당 45칸입니다. 같은 창고를 여러 명이 열면 같은 인벤토리 세션을 공유하므로 아이템을 넣거나 빼는 즉시 같이 보고 있는 플레이어에게 반영됩니다.

## 길드 기능

- 길드 생성: `/guild create <이름>`
- 길드 초대: `/guild invite <플레이어> [길드]`
- 초대 수락: `/guild accept <길드>`
- 초대 거절: `/guild deny <길드>`
- 받은 초대 목록: `/guild invites`
- 가입 길드 목록: `/guild list`
- 멤버 목록: `/guild members [길드]`
- 길드 창고 열기: `/guild storage [길드]`
- 길드 탈퇴: `/guild leave [길드]`
- 멤버 추방: `/guild kick <플레이어> [길드]`
- 길드 해체: `/guild disband [길드]`
- 관리자 승급: `/guild promote <플레이어> [길드]`
- 관리자 강등: `/guild demote <플레이어> [길드]`
- 길드 초대권 지급: `/guild ticket invite [수량]`

초대는 길드 초대권을 왼손에 들고 진행해야 합니다. 초대권을 왼손에 들고 우클릭하면 채팅 입력 모드가 열리고, 온라인 플레이어 이름을 입력하면 초대가 등록됩니다.

## 권한

```text
storageguild.admin
```

기본값은 OP입니다. 이 권한이 있어야 초대권/확장권 지급 명령어를 사용할 수 있습니다.

## 설정

`plugins/storageGuild/config.yml`

```yaml
storage:
  default-slots: 9
  slots-per-expansion: 9
  gui-size: 54
  page-item-slots: 45
  max-pages: 20

guild:
  invite-expire-minutes: 10
  max-name-length: 16
```

## PlaceholderAPI

Identifier는 `storageguild`입니다.

플레이어 기준 placeholder:

```text
%storageguild_guild_count%
%storageguild_guild_list%
%storageguild_first_guild%
%storageguild_invite_count%
%storageguild_invite_list%
%storageguild_personal_slots%
%storageguild_personal_pages%
%storageguild_guild_role_<길드>%
%storageguild_has_guild_<길드>%
%storageguild_can_manage_<길드>%
%storageguild_has_invite_<길드>%
```

길드 기준 placeholder:

```text
%storageguild_guild_members_<길드>%
%storageguild_guild_member_count_<길드>%
%storageguild_guild_storage_slots_<길드>%
%storageguild_guild_storage_pages_<길드>%
%storageguild_guild_owner_<길드>%
```

Boolean placeholder는 `true` 또는 `false`를 반환합니다.

## Skript

Skript가 설치되어 있으면 아래 구문이 자동 등록됩니다.

### Effects

```vb
open storageguild personal storage of %player%
open storageguild personal storage of %player% at page %number%
open storageguild guild storage %string% for %player%
open storageguild guild storage %string% for %player% at page %number%
open storageguild guild selector for %player%

create storageguild guild %string% for %player%
send storageguild guild invite to %player% for guild %string% by %player%
force send storageguild guild invite to %player% for guild %string% by %player%
make %player% accept storageguild guild invite %string%
make %player% deny storageguild guild invite %string%

expand storageguild personal storage of %player%
expand storageguild guild storage %string% for %player%

make %player% leave storageguild guild %string%
disband storageguild guild %string% by %player%
kick %player% from storageguild guild %string% by %player%
promote %player% in storageguild guild %string% by %player%
demote %player% in storageguild guild %string% by %player%

give %number% storageguild guild invite tickets to %player%
give %number% storageguild storage expansion tickets to %player%
```

`send storageguild guild invite ...`는 초대하는 플레이어가 왼손에 초대권을 들고 있어야 합니다. `force send ...`는 초대권 소모 없이 초대를 등록하지만, 초대하는 플레이어는 해당 길드의 관리자 권한이 있어야 합니다.

### Conditions

```vb
%player% is a member of storageguild guild %string%
%player% can manage storageguild guild %string%
%player% has a storageguild guild invite ticket
%player% has a storageguild storage expansion ticket
%player% has a storageguild invite for guild %string%
```

### Expressions

문자열 expression:

```vb
storageguild guilds of %player%
storageguild invite guilds of %player%
storageguild role of %player% in guild %string%
storageguild members of guild %string%
storageguild owner of guild %string%
```

숫자 expression:

```vb
storageguild guild count of %player%
storageguild invite count of %player%
storageguild personal storage slots of %player%
storageguild personal storage pages of %player%
storageguild guild storage slots of guild %string%
storageguild guild storage pages of guild %string%
storageguild member count of guild %string%
```

### Skript 예시

```vb
command /myguildstorage:
    trigger:
        open storageguild guild selector for player

command /giveguildtickets <player>:
    permission: storageguild.admin
    trigger:
        give 3 storageguild guild invite tickets to arg-1
        give 1 storageguild storage expansion ticket to arg-1

command /guildinfo <text>:
    trigger:
        send "멤버 수: %storageguild member count of guild arg-1%"
        send "창고 칸: %storageguild guild storage slots of guild arg-1%"
```

## 저장 방식

DB 파일은 DBManager가 관리하는 `plugins/storageGuild/databases/storageGuild.db`에 생성됩니다. 아이템은 Bukkit `ItemStack` 직렬화를 사용해서 저장하므로 아이템 타입, 수량, 메타데이터, 인챈트 등 Bukkit이 직렬화 가능한 데이터를 같이 보존합니다.
