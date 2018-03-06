Object.defineProperty(exports, "__esModule", { value: true });
var Rdf = require("../core/Rdf");
var prov;
(function (prov) {
    prov._NAMESPACE = 'http://www.w3.org/ns/prov#';
    prov.iri = function (s) { return Rdf.iri(prov._NAMESPACE + s); };
    prov.actedOnBehalfOf = prov.iri('actedOnBehalfOf');
    prov.Activity = prov.iri('Activity');
    prov.activity = prov.iri('activity');
    prov.ActivityInfluence = prov.iri('ActivityInfluence');
    prov.agent = prov.iri('agent');
    prov.Agent = prov.iri('Agent');
    prov.AgentInfluence = prov.iri('AgentInfluence');
    prov.alternateOf = prov.iri('alternateOf');
    prov.aq = prov.iri('aq');
    prov.Association = prov.iri('Association');
    prov.atLocation = prov.iri('atLocation');
    prov.atTime = prov.iri('atTime');
    prov.Attribution = prov.iri('Attribution');
    prov.Bundle = prov.iri('Bundle');
    prov.category = prov.iri('category');
    prov.Collection = prov.iri('Collection');
    prov.Communication = prov.iri('Communication');
    prov.component = prov.iri('component');
    prov.constraints = prov.iri('constraints');
    prov.definition = prov.iri('definition');
    prov.Delegation = prov.iri('Delegation');
    prov.Derivation = prov.iri('Derivation');
    prov.dm = prov.iri('dm');
    prov.editorialNote = prov.iri('editorialNote');
    prov.editorsDefinition = prov.iri('editorsDefinition');
    prov.EmptyCollection = prov.iri('EmptyCollection');
    prov.End = prov.iri('End');
    prov.endedAtTime = prov.iri('endedAtTime');
    prov.Entity = prov.iri('Entity');
    prov.entity = prov.iri('entity');
    prov.EntityInfluence = prov.iri('EntityInfluence');
    prov.generated = prov.iri('generated');
    prov.generatedAtTime = prov.iri('generatedAtTime');
    prov.Generation = prov.iri('Generation');
    prov.hadActivity = prov.iri('hadActivity');
    prov.hadGeneration = prov.iri('hadGeneration');
    prov.hadMember = prov.iri('hadMember');
    prov.hadPlan = prov.iri('hadPlan');
    prov.hadPrimarySource = prov.iri('hadPrimarySource');
    prov.hadRole = prov.iri('hadRole');
    prov.hadUsage = prov.iri('hadUsage');
    prov.Influence = prov.iri('Influence');
    prov.influenced = prov.iri('influenced');
    prov.influencer = prov.iri('influencer');
    prov.InstantaneousEvent = prov.iri('InstantaneousEvent');
    prov.invalidated = prov.iri('invalidated');
    prov.invalidatedAtTime = prov.iri('invalidatedAtTime');
    prov.Invalidation = prov.iri('Invalidation');
    prov.inverse = prov.iri('inverse');
    prov.Location = prov.iri('Location');
    prov.n = prov.iri('n');
    prov.order = prov.iri('order');
    prov.Organization = prov.iri('Organization');
    prov.Person = prov.iri('Person');
    prov.Plan = prov.iri('Plan');
    prov.PrimarySource = prov.iri('PrimarySource');
    prov.qualifiedAssociation = prov.iri('qualifiedAssociation');
    prov.qualifiedAttribution = prov.iri('qualifiedAttribution');
    prov.qualifiedCommunication = prov.iri('qualifiedCommunication');
    prov.qualifiedDelegation = prov.iri('qualifiedDelegation');
    prov.qualifiedDerivation = prov.iri('qualifiedDerivation');
    prov.qualifiedEnd = prov.iri('qualifiedEnd');
    prov.qualifiedForm = prov.iri('qualifiedForm');
    prov.qualifiedGeneration = prov.iri('qualifiedGeneration');
    prov.qualifiedInfluence = prov.iri('qualifiedInfluence');
    prov.qualifiedInvalidation = prov.iri('qualifiedInvalidation');
    prov.qualifiedPrimarySource = prov.iri('qualifiedPrimarySource');
    prov.qualifiedQuotation = prov.iri('qualifiedQuotation');
    prov.qualifiedRevision = prov.iri('qualifiedRevision');
    prov.qualifiedStart = prov.iri('qualifiedStart');
    prov.qualifiedUsage = prov.iri('qualifiedUsage');
    prov.Quotation = prov.iri('Quotation');
    prov.Revision = prov.iri('Revision');
    prov.Role = prov.iri('Role');
    prov.sharesDefinitionWith = prov.iri('sharesDefinitionWith');
    prov.SoftwareAgent = prov.iri('SoftwareAgent');
    prov.specializationOf = prov.iri('specializationOf');
    prov.Start = prov.iri('Start');
    prov.startedAtTime = prov.iri('startedAtTime');
    prov.todo = prov.iri('todo');
    prov.unqualifiedForm = prov.iri('unqualifiedForm');
    prov.Usage = prov.iri('Usage');
    prov.used = prov.iri('used');
    prov.value = prov.iri('value');
    prov.wasAssociatedWith = prov.iri('wasAssociatedWith');
    prov.wasAttributedTo = prov.iri('wasAttributedTo');
    prov.wasDerivedFrom = prov.iri('wasDerivedFrom');
    prov.wasEndedBy = prov.iri('wasEndedBy');
    prov.wasGeneratedBy = prov.iri('wasGeneratedBy');
    prov.wasInfluencedBy = prov.iri('wasInfluencedBy');
    prov.wasInformedBy = prov.iri('wasInformedBy');
    prov.wasInvalidatedBy = prov.iri('wasInvalidatedBy');
    prov.wasQuotedFrom = prov.iri('wasQuotedFrom');
    prov.wasRevisionOf = prov.iri('wasRevisionOf');
    prov.wasStartedBy = prov.iri('wasStartedBy');
})(prov || (prov = {}));
exports.default = prov;