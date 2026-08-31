package com.acme.clm.repo;

import com.acme.clm.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * All Spring Data repositories for the platform, grouped as nested interfaces.
 * Spring Data picks up nested repository interfaces within a scanned package.
 */
public final class Repos {
    private Repos() {}

    public interface Users extends JpaRepository<AppUser, UUID> {
        Optional<AppUser> findByEmailIgnoreCase(String email);
    }

    public interface LegalEntities extends JpaRepository<LegalEntity, UUID> {
        Optional<LegalEntity> findByShortName(String shortName);
    }

    public interface LegalTeams extends JpaRepository<LegalTeam, UUID> {}

    public interface SigningAuthorities extends JpaRepository<SigningAuthority, UUID> {
        List<SigningAuthority> findByLegalEntityIdAndUserId(UUID legalEntityId, UUID userId);
        List<SigningAuthority> findByLegalEntityId(UUID legalEntityId);
    }

    public interface ContractTypes extends JpaRepository<ContractTypeDefinition, String> {
        List<ContractTypeDefinition> findByIsActiveTrue();
    }

    public interface Parties extends JpaRepository<Party, UUID> {
        List<Party> findTop10ByLegalNameContainingIgnoreCaseOrTradingNameContainingIgnoreCase(String a, String b);
    }

    public interface Contracts extends JpaRepository<Contract, UUID> {
        Optional<Contract> findByContractNumber(String contractNumber);
        List<Contract> findByParentContractId(UUID parentContractId);
        List<Contract> findByContractTypeCodeAndStatus(String type, String status);
        long countByContractTypeCode(String type);
    }

    public interface ContractParties extends JpaRepository<ContractParty, ContractParty.Key> {
        List<ContractParty> findByContractId(UUID contractId);
        List<ContractParty> findByPartyId(UUID partyId);
    }

    public interface ContractTerms extends JpaRepository<ContractTerm, UUID> {
        List<ContractTerm> findByContractId(UUID contractId);
        List<ContractTerm> findByContractIdIn(List<UUID> contractIds);
    }

    public interface ContractVersions extends JpaRepository<ContractVersion, UUID> {
        List<ContractVersion> findByContractIdOrderByVersionNoDesc(UUID contractId);
    }

    public interface ClauseConcepts extends JpaRepository<ClauseConcept, UUID> {
        Optional<ClauseConcept> findByConceptCode(String code);
    }

    public interface ClauseVariants extends JpaRepository<ClauseVariant, UUID> {
        List<ClauseVariant> findByClauseConceptId(UUID conceptId);
        List<ClauseVariant> findByStatus(String status);
    }

    public interface ClauseVariantUsages extends JpaRepository<ClauseVariantUsage, UUID> {
        List<ClauseVariantUsage> findByContractId(UUID contractId);
        List<ClauseVariantUsage> findByClauseVariantId(UUID variantId);
    }

    public interface Templates extends JpaRepository<Template, UUID> {
        List<Template> findByContractTypeCode(String code);
    }

    public interface TemplateSections extends JpaRepository<TemplateSection, UUID> {
        List<TemplateSection> findByTemplateIdOrderBySortOrder(UUID templateId);
    }

    public interface MergeFields extends JpaRepository<MergeField, UUID> {
        List<MergeField> findByTemplateId(UUID templateId);
    }

    public interface WorkflowDefinitions extends JpaRepository<WorkflowDefinition, UUID> {
        List<WorkflowDefinition> findByKeyOrderByVersionNoDesc(String key);
    }

    public interface WorkflowInstances extends JpaRepository<WorkflowInstance, UUID> {
        List<WorkflowInstance> findByContractId(UUID contractId);
        List<WorkflowInstance> findByStatus(String status);
    }

    public interface WorkflowTasks extends JpaRepository<WorkflowTask, UUID> {
        List<WorkflowTask> findByWorkflowInstanceId(UUID instanceId);
        List<WorkflowTask> findByAssignedUserIdAndStatus(UUID userId, String status);
        List<WorkflowTask> findByStatus(String status);
    }

    public interface AssignmentRules extends JpaRepository<AssignmentRule, UUID> {
        List<AssignmentRule> findByIsActiveTrueOrderByPriorityAsc();
    }

    public interface Obligations extends JpaRepository<Obligation, UUID> {
        List<Obligation> findByContractId(UUID contractId);
        List<Obligation> findByStatus(String status);
    }

    public interface AuditEvents extends JpaRepository<AuditEvent, UUID> {
        List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String type, String id);
        List<AuditEvent> findTop100ByOrderByOccurredAtDesc();
        List<AuditEvent> findTop200ByEntityTypeAndActionOrderByOccurredAtDesc(String entityType, String action);
    }

    public interface IntakeSessions extends JpaRepository<IntakeSession, UUID> {
        List<IntakeSession> findByRequesterUserIdOrderByUpdatedAtDesc(UUID userId);
        java.util.Optional<IntakeSession> findTopByRequestNumberStartingWithOrderByRequestNumberDesc(String prefix);
        java.util.Optional<IntakeSession> findFirstByResultingContractIdOrderByUpdatedAtDesc(UUID resultingContractId);
    }

    public interface IntakeAttachments extends JpaRepository<IntakeAttachment, UUID> {
        List<IntakeAttachment> findByIntakeSessionIdOrderByCreatedAtAsc(UUID intakeSessionId);
        void deleteByIntakeSessionId(UUID intakeSessionId);
    }

    public interface ContractAttachments extends JpaRepository<ContractAttachment, UUID> {
        List<ContractAttachment> findByContractIdOrderByCreatedAtAsc(UUID contractId);
    }

    public interface ContractBriefings extends JpaRepository<ContractBriefing, UUID> {
        java.util.Optional<ContractBriefing> findByContractId(UUID contractId);
    }

    public interface AiInteractions extends JpaRepository<AiInteraction, UUID> {
        List<AiInteraction> findTop200ByOrderByOccurredAtDesc();
        List<AiInteraction> findByContractIdOrderByOccurredAtDesc(UUID contractId);
    }

    public interface PrecedentLinks extends JpaRepository<PrecedentLink, UUID> {
        List<PrecedentLink> findByContractId(UUID contractId);
    }

    public interface ContractRelations extends JpaRepository<ContractRelation, UUID> {
        List<ContractRelation> findByContractIdOrderByCreatedAtDesc(UUID contractId);
        List<ContractRelation> findByRelatedContractId(UUID relatedContractId);
    }

    public interface SavedReports extends JpaRepository<SavedReport, UUID> {
        List<SavedReport> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
    }

    public interface CommentThreads extends JpaRepository<CommentThread, UUID> {
        List<CommentThread> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);
    }

    public interface CommentMessages extends JpaRepository<CommentMessage, UUID> {
        List<CommentMessage> findByThreadIdOrderByCreatedAtAsc(UUID threadId);
        long countByThreadId(UUID threadId);
    }

    public interface ContractParticipants extends JpaRepository<ContractParticipant, ContractParticipant.Key> {
        List<ContractParticipant> findByContractId(UUID contractId);
        List<ContractParticipant> findByUserId(UUID userId);
        void deleteByContractIdAndUserId(UUID contractId, UUID userId);
    }

    public interface AccessDimensions extends JpaRepository<AccessDimension, String> {
        List<AccessDimension> findByIsActiveTrueOrderBySortOrderAsc();
    }

    public interface AccessGrants extends JpaRepository<AccessGrant, UUID> {
        List<AccessGrant> findByUserIdAndStatus(UUID userId, String status);
        List<AccessGrant> findByStatus(String status);
    }

    public interface AccessRequests extends JpaRepository<AccessRequest, UUID> {
        List<AccessRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);
        List<AccessRequest> findByStatusOrderByCreatedAtDesc(String status);
    }

    public interface ApproverScopes extends JpaRepository<ApproverScope, UUID> {
        List<ApproverScope> findByIsActiveTrue();
        List<ApproverScope> findByUserIdAndIsActiveTrue(UUID userId);
    }

    public interface AiReviewRules extends JpaRepository<AiReviewRule, UUID> {
        List<AiReviewRule> findByIsActiveTrueOrderBySortAsc();
        List<AiReviewRule> findAllByOrderBySortAsc();
        Optional<AiReviewRule> findByCodeIgnoreCase(String code);
    }

    public interface Playbooks extends JpaRepository<Playbook, UUID> {
        List<Playbook> findByIsActiveTrue();
    }

    public interface ReviewRuns extends JpaRepository<AiReviewRun, UUID> {
        Optional<AiReviewRun> findFirstByContractIdOrderByCreatedAtDesc(UUID contractId);
        List<AiReviewRun> findByContractIdOrderByCreatedAtDesc(UUID contractId);
    }

    public interface ContractRisks extends JpaRepository<ContractRisk, UUID> {
        List<ContractRisk> findByContractIdOrderByCreatedAtDesc(UUID contractId);
        List<ContractRisk> findByContractIdAndStatus(UUID contractId, String status);
    }

    public interface AutoRejectRules extends JpaRepository<AutoRejectRule, UUID> {
        List<AutoRejectRule> findByOwnerUserIdOrderByCreatedAtDesc(UUID ownerUserId);
        List<AutoRejectRule> findByOwnerUserIdAndEnabledTrue(UUID ownerUserId);
        List<AutoRejectRule> findAllByOrderByCreatedAtDesc();
    }

    /** Keyed by owner user id — one global trigger-timing setting per approver. */
    public interface AutoRejectSettings extends JpaRepository<AutoRejectSetting, UUID> {}

    public interface UserSettings extends JpaRepository<UserSetting, UserSetting.Key> {}

    public interface Metrics extends JpaRepository<Contract, UUID> {
        @Query(value = """
            SELECT c.status AS k, COUNT(*) AS v FROM contract c GROUP BY c.status
            """, nativeQuery = true)
        List<Object[]> countByStatus();

        @Query(value = """
            SELECT le.short_name AS k, COUNT(*) AS v
            FROM contract c JOIN legal_entity le ON le.id = c.contracting_entity_id
            GROUP BY le.short_name ORDER BY 2 DESC
            """, nativeQuery = true)
        List<Object[]> countByEntity();

        @Query(value = """
            SELECT c.contract_type_code AS k, COUNT(*) AS v FROM contract c
            GROUP BY c.contract_type_code ORDER BY 2 DESC
            """, nativeQuery = true)
        List<Object[]> countByType();
    }
}
