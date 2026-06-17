package net.unitedcraft.managers;

import net.unitedcraft.UnitedCraft;
import net.unitedcraft.models.Nation;
import net.unitedcraft.models.NationMember;
import net.unitedcraft.repository.NationRepository;

import java.util.*;

public class NationManager {

    private final NationRepository repo = new NationRepository();

    public Nation createNation(String name, UUID founderUuid, String founderName,
                                Nation.GovernmentType govType) throws Exception {
        if (repo.getNationByName(name).isPresent()) {
            throw new Exception("A nation named '" + name + "' already exists.");
        }
        if (repo.getMember(founderUuid).map(m -> m.getNationId() != null).orElse(false)) {
            throw new Exception("You are already in a nation. Leave it first.");
        }

        String id = UUID.randomUUID().toString();
        Nation nation = new Nation(id, name, govType, System.currentTimeMillis());
        repo.saveNation(nation);

        NationMember founder = new NationMember(
            founderUuid, founderName, id, NationMember.Role.PRESIDENT, System.currentTimeMillis()
        );
        repo.saveMember(founder);

        UnitedCraft.LOGGER.info("Nation created: " + name + " by " + founderName);
        return nation;
    }

    public void disbandNation(String nationId) {
        List<NationMember> members = repo.getMembersByNation(nationId);
        for (NationMember m : members) {
            m.setNationId(null);
            m.setRole(NationMember.Role.CIVILIAN);
            repo.saveMember(m);
        }
        repo.getClaimsByNation(nationId).forEach(c -> repo.deleteClaim(c.getChunkKey()));
        repo.deleteNation(nationId);
    }

    public boolean joinNation(UUID playerUuid, String playerName, String nationId) throws Exception {
        Optional<NationMember> existing = repo.getMember(playerUuid);
        if (existing.isPresent() && existing.get().getNationId() != null) {
            throw new Exception("You are already in a nation.");
        }

        Optional<Nation> nation = repo.getNationById(nationId);
        if (nation.isEmpty()) throw new Exception("Nation not found.");

        if (!nation.get().isOpenRecruitment()) {
            throw new Exception("This nation requires an invitation to join.");
        }

        // New players join as CIVILIAN
        NationMember member = new NationMember(
            playerUuid, playerName, nationId, NationMember.Role.CIVILIAN, System.currentTimeMillis()
        );
        repo.saveMember(member);
        return true;
    }

    public void leaveNation(UUID playerUuid) throws Exception {
        Optional<NationMember> member = repo.getMember(playerUuid);
        if (member.isEmpty() || member.get().getNationId() == null) {
            throw new Exception("You are not in a nation.");
        }
        if (member.get().isPresident()) {
            throw new Exception("You are the President. Transfer leadership or disband the nation first.");
        }
        NationMember m = member.get();
        m.setNationId(null);
        m.setRole(NationMember.Role.CIVILIAN);
        repo.saveMember(m);
    }

    public void setRole(String nationId, UUID targetUuid, NationMember.Role newRole) throws Exception {
        Optional<NationMember> target = repo.getMember(targetUuid);
        if (target.isEmpty() || !nationId.equals(target.get().getNationId())) {
            throw new Exception("That player is not in your nation.");
        }
        if (newRole == NationMember.Role.PRESIDENT) {
            throw new Exception("Cannot directly assign President. Use /nation transfer.");
        }
        NationMember m = target.get();
        m.setRole(newRole);
        repo.saveMember(m);
    }

    public void transferLeadership(String nationId, UUID currentPresidentUuid, UUID newPresidentUuid) throws Exception {
        Optional<NationMember> current = repo.getMember(currentPresidentUuid);
        Optional<NationMember> newPres = repo.getMember(newPresidentUuid);

        if (current.isEmpty() || !current.get().isPresident()) {
            throw new Exception("You are not the President.");
        }
        if (newPres.isEmpty() || !nationId.equals(newPres.get().getNationId())) {
            throw new Exception("That player is not in your nation.");
        }

        current.get().setRole(NationMember.Role.VICE_PRESIDENT);
        newPres.get().setRole(NationMember.Role.PRESIDENT);
        repo.saveMember(current.get());
        repo.saveMember(newPres.get());
    }

    public Optional<Nation> getNationOfPlayer(UUID uuid) {
        Optional<NationMember> member = repo.getMember(uuid);
        if (member.isEmpty() || member.get().getNationId() == null) return Optional.empty();
        return repo.getNationById(member.get().getNationId());
    }

    public Optional<NationMember> getMember(UUID uuid) { return repo.getMember(uuid); }
    public Optional<Nation> getNationByName(String name) { return repo.getNationByName(name); }
    public Optional<Nation> getNationById(String id) { return repo.getNationById(id); }
    public List<Nation> getAllNations() { return repo.getAllNations(); }
    public List<NationMember> getMembers(String nationId) { return repo.getMembersByNation(nationId); }
    public NationRepository getRepo() { return repo; }

    // Claim limit = 10 base + 2 per member
    public int getClaimLimit(String nationId) {
        return 10 + (repo.getMemberCount(nationId) * 2);
    }
}
