package com.drewdrew1.guild;

import com.drewdrew1.Main;
import com.drewdrew1.config.StorageGuildConfig;
import com.drewdrew1.db.StorageRepository;
import com.drewdrew1.model.Guild;
import com.drewdrew1.model.GuildMember;
import com.drewdrew1.model.GuildRole;
import com.drewdrew1.ticket.TicketService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class GuildService {
    private final Main plugin;
    private final StorageRepository repository;
    private final TicketService ticketService;
    private final StorageGuildConfig config;
    private final Map<UUID, InvitePrompt> prompts = new ConcurrentHashMap<>();

    public GuildService(Main plugin, StorageRepository repository, TicketService ticketService, StorageGuildConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.ticketService = ticketService;
        this.config = config;
        repository.cleanupExpiredInvites();
    }

    public CompletableFuture<List<Guild>> guildsFor(UUID playerUuid) {
        return repository.guildsForMember(playerUuid);
    }

    public CompletableFuture<Optional<Guild>> guild(String name) {
        return repository.findGuildByName(name);
    }

    public CompletableFuture<Optional<GuildMember>> member(long guildId, UUID playerUuid) {
        return repository.findMember(guildId, playerUuid);
    }

    public void createGuild(Player player, String guildName) {
        if (!validGuildName(guildName)) {
            player.sendMessage("길드 이름은 3-" + config.maxGuildNameLength() + "자의 한글/영문/숫자/_ 만 사용할 수 있습니다.");
            return;
        }
        repository.createGuild(player.getUniqueId(), player.getName(), guildName)
                .whenComplete((guild, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        player.sendMessage("길드를 만들지 못했습니다: " + rootMessage(throwable));
                        return;
                    }
                    player.sendMessage(guild.name() + " 길드를 만들었습니다.");
                }));
    }

    public void inviteByCommand(Player inviter, String targetName, String guildName) {
        if (!ticketService.hasInviteTicketInOffhand(inviter)) {
            inviter.sendMessage("길드 초대권을 왼손에 들고 있어야 합니다.");
            return;
        }
        resolveManagedGuild(inviter, guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null) {
                inviter.sendMessage(rootMessage(throwable));
                return;
            }
            inviteToGuild(inviter, guild, targetName, true);
        }));
    }

    public void forceInvite(Player inviter, String targetName, String guildName) {
        resolveManagedGuild(inviter, guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null) {
                inviter.sendMessage(rootMessage(throwable));
                return;
            }
            inviteToGuild(inviter, guild, targetName, false);
        }));
    }

    public void startInvitePrompt(Player player) {
        if (!ticketService.hasInviteTicketInOffhand(player)) {
            player.sendMessage("길드 초대권을 왼손에 들고 우클릭하세요.");
            return;
        }
        manageableGuilds(player.getUniqueId()).whenComplete((managedGuilds, throwable) -> runSync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (throwable != null) {
                player.sendMessage("초대 가능한 길드를 확인하지 못했습니다: " + rootMessage(throwable));
                return;
            }
            if (managedGuilds.isEmpty()) {
                player.sendMessage("초대 권한이 있는 길드가 없습니다.");
                return;
            }
            if (managedGuilds.size() == 1) {
                prompts.put(player.getUniqueId(), new InvitePrompt(PromptMode.TARGET_PLAYER, managedGuilds.getFirst().guild().id()));
                player.sendMessage("초대할 온라인 플레이어 이름을 채팅에 입력하세요. 취소하려면 '취소'를 입력하세요.");
                return;
            }
            prompts.put(player.getUniqueId(), new InvitePrompt(PromptMode.SELECT_GUILD, null));
            player.sendMessage("초대할 길드 이름을 먼저 입력하세요: " + joinGuildNames(managedGuilds));
        }));
    }

    public boolean hasPrompt(UUID playerUuid) {
        return prompts.containsKey(playerUuid);
    }

    public void handlePrompt(Player player, String message) {
        InvitePrompt prompt = prompts.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        if ("취소".equalsIgnoreCase(message) || "cancel".equalsIgnoreCase(message)) {
            prompts.remove(player.getUniqueId());
            player.sendMessage("초대 입력을 취소했습니다.");
            return;
        }
        if (prompt.mode() == PromptMode.SELECT_GUILD) {
            manageableGuilds(player.getUniqueId()).whenComplete((managedGuilds, throwable) -> runSync(() -> {
                if (throwable != null) {
                    prompts.remove(player.getUniqueId());
                    player.sendMessage("길드를 확인하지 못했습니다: " + rootMessage(throwable));
                    return;
                }
                ManagedGuild selected = managedGuilds.stream()
                        .filter(managed -> managed.guild().name().equalsIgnoreCase(message))
                        .findFirst()
                        .orElse(null);
                if (selected == null) {
                    player.sendMessage("초대 권한이 있는 길드 이름을 입력하세요: " + joinGuildNames(managedGuilds));
                    return;
                }
                prompts.put(player.getUniqueId(), new InvitePrompt(PromptMode.TARGET_PLAYER, selected.guild().id()));
                player.sendMessage("초대할 온라인 플레이어 이름을 채팅에 입력하세요.");
            }));
            return;
        }

        prompts.remove(player.getUniqueId());
        repository.findGuildById(prompt.guildId()).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null || guild.isEmpty()) {
                player.sendMessage("길드를 찾지 못했습니다.");
                return;
            }
            inviteToGuild(player, guild.get(), message, true);
        }));
    }

    public void acceptInvite(Player player, String guildName) {
        repository.acceptInvite(player.getUniqueId(), player.getName(), guildName)
                .whenComplete((guild, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        player.sendMessage("초대를 수락하지 못했습니다: " + rootMessage(throwable));
                        return;
                    }
                    player.sendMessage(guild.name() + " 길드에 가입했습니다.");
                }));
    }

    public void denyInvite(Player player, String guildName) {
        repository.denyInvite(player.getUniqueId(), guildName)
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        player.sendMessage("초대를 거절하지 못했습니다: " + rootMessage(throwable));
                        return;
                    }
                    player.sendMessage(guildName + " 길드 초대를 거절했습니다.");
                }));
    }

    public void showInvites(Player player) {
        repository.invitesFor(player.getUniqueId()).whenComplete((guilds, throwable) -> runSync(() -> {
            if (throwable != null) {
                player.sendMessage("초대 목록을 불러오지 못했습니다: " + rootMessage(throwable));
                return;
            }
            if (guilds.isEmpty()) {
                player.sendMessage("받은 길드 초대가 없습니다.");
                return;
            }
            player.sendMessage("받은 초대: " + String.join(", ", guilds.stream().map(Guild::name).toList()));
        }));
    }

    public void leaveGuild(Player player, String guildName) {
        resolveJoinedGuild(player, guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null) {
                player.sendMessage(rootMessage(throwable));
                return;
            }
            member(guild.id(), player.getUniqueId()).whenComplete((member, memberThrowable) -> runSync(() -> {
                if (memberThrowable != null || member.isEmpty()) {
                    player.sendMessage("길드 멤버 정보를 찾지 못했습니다.");
                    return;
                }
                if (member.get().role() == GuildRole.OWNER) {
                    player.sendMessage("길드장은 /guild disband <길드> 로 해체해야 합니다.");
                    return;
                }
                repository.removeMember(guild.id(), player.getUniqueId()).whenComplete((ignored, removeThrowable) -> runSync(() -> {
                    if (removeThrowable != null) {
                        player.sendMessage("길드에서 나가지 못했습니다: " + rootMessage(removeThrowable));
                        return;
                    }
                    player.sendMessage(guild.name() + " 길드에서 나갔습니다.");
                }));
            }));
        }));
    }

    public void kick(Player actor, String targetName, String guildName) {
        resolveManagedGuild(actor, guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null) {
                actor.sendMessage(rootMessage(throwable));
                return;
            }
            repository.findMember(guild.id(), actor.getUniqueId())
                    .thenCombine(repository.findMemberByName(guild.id(), targetName), (actorMember, targetMember) -> new MemberPair(actorMember, targetMember))
                    .whenComplete((pair, pairThrowable) -> runSync(() -> {
                        if (pairThrowable != null || pair.target().isEmpty() || pair.actor().isEmpty()) {
                            actor.sendMessage("대상 멤버를 찾지 못했습니다.");
                            return;
                        }
                        GuildMember actorMember = pair.actor().get();
                        GuildMember target = pair.target().get();
                        if (!actorMember.role().isHigherThan(target.role())) {
                            actor.sendMessage("같거나 높은 권한의 멤버는 추방할 수 없습니다.");
                            return;
                        }
                        repository.removeMember(guild.id(), target.playerUuid()).whenComplete((ignored, removeThrowable) -> runSync(() -> {
                            if (removeThrowable != null) {
                                actor.sendMessage("추방하지 못했습니다: " + rootMessage(removeThrowable));
                                return;
                            }
                            actor.sendMessage(target.playerName() + "님을 " + guild.name() + " 길드에서 추방했습니다.");
                            Player targetPlayer = Bukkit.getPlayer(target.playerUuid());
                            if (targetPlayer != null) {
                                targetPlayer.sendMessage(guild.name() + " 길드에서 추방되었습니다.");
                            }
                        }));
                    }));
        }));
    }

    public void disband(Player actor, String guildName) {
        resolveJoinedGuild(actor, guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null) {
                actor.sendMessage(rootMessage(throwable));
                return;
            }
            member(guild.id(), actor.getUniqueId()).whenComplete((member, memberThrowable) -> runSync(() -> {
                if (memberThrowable != null || member.isEmpty() || member.get().role() != GuildRole.OWNER) {
                    actor.sendMessage("길드장만 길드를 해체할 수 있습니다.");
                    return;
                }
                repository.deleteGuild(guild.id()).whenComplete((ignored, deleteThrowable) -> runSync(() -> {
                    if (deleteThrowable != null) {
                        actor.sendMessage("길드를 해체하지 못했습니다: " + rootMessage(deleteThrowable));
                        return;
                    }
                    actor.sendMessage(guild.name() + " 길드를 해체했습니다.");
                }));
            }));
        }));
    }

    public void promote(Player actor, String targetName, String guildName) {
        setAdminRole(actor, targetName, guildName, true);
    }

    public void demote(Player actor, String targetName, String guildName) {
        setAdminRole(actor, targetName, guildName, false);
    }

    public void showMembers(Player player, String guildName) {
        resolveJoinedGuild(player, guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null) {
                player.sendMessage(rootMessage(throwable));
                return;
            }
            repository.members(guild.id()).whenComplete((members, memberThrowable) -> runSync(() -> {
                if (memberThrowable != null) {
                    player.sendMessage("멤버 목록을 불러오지 못했습니다: " + rootMessage(memberThrowable));
                    return;
                }
                player.sendMessage(guild.name() + " 멤버: " + String.join(", ", members.stream()
                        .map(member -> member.playerName() + "(" + member.role().name() + ")")
                        .toList()));
            }));
        }));
    }

    public void listGuilds(Player player) {
        guildsFor(player.getUniqueId()).whenComplete((guilds, throwable) -> runSync(() -> {
            if (throwable != null) {
                player.sendMessage("길드 목록을 불러오지 못했습니다: " + rootMessage(throwable));
                return;
            }
            if (guilds.isEmpty()) {
                player.sendMessage("가입한 길드가 없습니다.");
                return;
            }
            player.sendMessage("가입한 길드: " + String.join(", ", guilds.stream().map(Guild::name).toList()));
        }));
    }

    public CompletableFuture<Guild> resolveJoinedGuild(Player player, String guildName) {
        if (guildName != null && !guildName.isBlank()) {
            return repository.findGuildByName(guildName).thenCompose(guild -> {
                if (guild.isEmpty()) {
                    return failed("길드를 찾지 못했습니다.");
                }
                return repository.findMember(guild.get().id(), player.getUniqueId()).thenCompose(member -> {
                    if (member.isEmpty()) {
                        return failed("가입하지 않은 길드입니다.");
                    }
                    return CompletableFuture.completedFuture(guild.get());
                });
            });
        }
        return repository.guildsForMember(player.getUniqueId()).thenCompose(guilds -> {
            if (guilds.isEmpty()) {
                return failed("가입한 길드가 없습니다.");
            }
            if (guilds.size() > 1) {
                return failed("길드 이름을 입력하세요: " + String.join(", ", guilds.stream().map(Guild::name).toList()));
            }
            return CompletableFuture.completedFuture(guilds.getFirst());
        });
    }

    private CompletableFuture<Guild> resolveManagedGuild(Player player, String guildName) {
        return manageableGuilds(player.getUniqueId()).thenCompose(managedGuilds -> {
            if (managedGuilds.isEmpty()) {
                return failed("관리 권한이 있는 길드가 없습니다.");
            }
            if (guildName == null || guildName.isBlank()) {
                if (managedGuilds.size() == 1) {
                    return CompletableFuture.completedFuture(managedGuilds.getFirst().guild());
                }
                return failed("길드 이름을 입력하세요: " + joinGuildNames(managedGuilds));
            }
            return managedGuilds.stream()
                    .filter(managed -> managed.guild().name().equalsIgnoreCase(guildName))
                    .map(ManagedGuild::guild)
                    .findFirst()
                    .map(CompletableFuture::completedFuture)
                    .orElseGet(() -> failed("관리 권한이 있는 길드가 아니거나 길드를 찾지 못했습니다."));
        });
    }

    private CompletableFuture<List<ManagedGuild>> manageableGuilds(UUID playerUuid) {
        return repository.guildsForMember(playerUuid).thenCompose(guilds -> {
            List<CompletableFuture<Optional<GuildMember>>> futures = guilds.stream()
                    .map(guild -> repository.findMember(guild.id(), playerUuid))
                    .toList();
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
                List<ManagedGuild> managedGuilds = new ArrayList<>();
                for (int index = 0; index < guilds.size(); index++) {
                    Optional<GuildMember> member = futures.get(index).join();
                    if (member.isPresent() && member.get().role().canManageMembers()) {
                        managedGuilds.add(new ManagedGuild(guilds.get(index), member.get()));
                    }
                }
                return managedGuilds;
            });
        });
    }

    private void inviteToGuild(Player inviter, Guild guild, String targetName, boolean requireTicket) {
        if (requireTicket && !ticketService.hasInviteTicketInOffhand(inviter)) {
            inviter.sendMessage("길드 초대권을 왼손에 들고 있어야 합니다.");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            inviter.sendMessage("온라인 플레이어만 초대할 수 있습니다.");
            return;
        }
        if (target.getUniqueId().equals(inviter.getUniqueId())) {
            inviter.sendMessage("자기 자신은 초대할 수 없습니다.");
            return;
        }
        long expiresAt = System.currentTimeMillis() + config.inviteExpireMinutes() * 60_000L;
        repository.createInvite(guild.id(), inviter.getUniqueId(), target.getUniqueId(), target.getName(), expiresAt)
                .whenComplete((ignored, throwable) -> runSync(() -> {
                    if (throwable != null) {
                        inviter.sendMessage("초대를 등록하지 못했습니다: " + rootMessage(throwable));
                        return;
                    }
                    if (requireTicket && !ticketService.consumeInviteTicketFromOffhand(inviter)) {
                        inviter.sendMessage("초대권이 사라져 초대를 완료하지 못했습니다.");
                        repository.denyInvite(target.getUniqueId(), guild.name());
                        return;
                    }
                    inviter.sendMessage(target.getName() + "님에게 " + guild.name() + " 길드 초대를 보냈습니다.");
                    target.sendMessage(guild.name() + " 길드 초대를 받았습니다. /guild accept " + guild.name() + " 로 수락하세요.");
                }));
    }

    private void setAdminRole(Player actor, String targetName, String guildName, boolean promote) {
        resolveJoinedGuild(actor, guildName).whenComplete((guild, throwable) -> runSync(() -> {
            if (throwable != null) {
                actor.sendMessage(rootMessage(throwable));
                return;
            }
            repository.findMember(guild.id(), actor.getUniqueId())
                    .thenCombine(repository.findMemberByName(guild.id(), targetName), (actorMember, targetMember) -> new MemberPair(actorMember, targetMember))
                    .whenComplete((pair, pairThrowable) -> runSync(() -> {
                        if (pairThrowable != null || pair.actor().isEmpty() || pair.target().isEmpty()) {
                            actor.sendMessage("대상 멤버를 찾지 못했습니다.");
                            return;
                        }
                        if (pair.actor().get().role() != GuildRole.OWNER) {
                            actor.sendMessage("길드장만 관리자 권한을 변경할 수 있습니다.");
                            return;
                        }
                        GuildMember target = pair.target().get();
                        if (target.role() == GuildRole.OWNER) {
                            actor.sendMessage("길드장 권한은 변경할 수 없습니다.");
                            return;
                        }
                        GuildRole next = promote ? GuildRole.ADMIN : GuildRole.MEMBER;
                        repository.updateRole(guild.id(), target.playerUuid(), next).whenComplete((ignored, updateThrowable) -> runSync(() -> {
                            if (updateThrowable != null) {
                                actor.sendMessage("권한을 변경하지 못했습니다: " + rootMessage(updateThrowable));
                                return;
                            }
                            actor.sendMessage(target.playerName() + "님의 권한을 " + next.name() + "로 변경했습니다.");
                        }));
                    }));
        }));
    }

    private boolean validGuildName(String name) {
        if (name == null || name.length() < 3 || name.length() > config.maxGuildNameLength()) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).matches("[a-z0-9_가-힣]+");
    }

    private String joinGuildNames(List<ManagedGuild> managedGuilds) {
        return String.join(", ", managedGuilds.stream().map(managed -> managed.guild().name()).toList());
    }

    private <T> CompletableFuture<T> failed(String message) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException(message));
        return future;
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null) {
            return current.getClass().getSimpleName();
        }
        if (message.contains("UNIQUE")) {
            return "이미 사용 중인 이름입니다.";
        }
        if (message.contains("already a guild member")) {
            return "이미 해당 길드의 멤버입니다.";
        }
        if (message.contains("Invite expired")) {
            return "초대가 만료되었습니다.";
        }
        if (message.contains("Invite not found")) {
            return "초대를 찾지 못했습니다.";
        }
        return message;
    }

    private enum PromptMode {
        SELECT_GUILD,
        TARGET_PLAYER
    }

    private record InvitePrompt(PromptMode mode, Long guildId) {
    }

    private record ManagedGuild(Guild guild, GuildMember member) {
    }

    private record MemberPair(Optional<GuildMember> actor, Optional<GuildMember> target) {
    }
}
