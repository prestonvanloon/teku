/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.spec.logic.versions.merge.block;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.merge.BeaconBlockBodyMerge;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.merge.MutableBeaconStateMerge;
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.operations.validation.AttestationDataStateTransitionValidator;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.merge.helpers.MiscHelpersMerge;

public class BlockProcessorMerge extends AbstractBlockProcessor {

  private static final Logger LOG = LogManager.getLogger();

  private final MiscHelpersMerge miscHelpersMerge;
  private final ExecutionPayloadUtil executionPayloadUtil;

  public BlockProcessorMerge(
      final SpecConfigMerge specConfig,
      final Predicates predicates,
      final MiscHelpersMerge miscHelpers,
      final BeaconStateAccessors beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil,
      final AttestationDataStateTransitionValidator attestationValidator,
      final ExecutionPayloadUtil executionPayloadUtil) {
    super(
        specConfig,
        predicates,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        beaconStateUtil,
        attestationUtil,
        validatorsUtil,
        attestationValidator);

    this.miscHelpersMerge = miscHelpers;
    this.executionPayloadUtil = executionPayloadUtil;
  }

  @Override
  public void processSyncCommittee(MutableBeaconState state, SyncAggregate syncAggregate)
      throws BlockProcessingException {
    throw new UnsupportedOperationException("No SyncCommittee in merge");
  }

  @Override
  public void processExecutionPayload(MutableBeaconState genericState, BeaconBlockBody genericBody)
      throws BlockProcessingException {
    try {
      final MutableBeaconStateMerge state = MutableBeaconStateMerge.required(genericState);
      final BeaconBlockBodyMerge blockBody = BeaconBlockBodyMerge.required(genericBody);
      final ExecutionPayload executionPayload = blockBody.getExecution_payload();

      // Pre-merge, skip processing
      if (!miscHelpersMerge.isTransitionCompleted(state)
          && !miscHelpersMerge.isTransitionBlock(state, blockBody)) {
        return;
      }

      if (miscHelpersMerge.isTransitionCompleted(state)) {
        checkArgument(
            executionPayload
                .getParent_hash()
                .equals(state.getLatest_execution_payload_header().getBlock_hash()),
            "process_execution_payload: Verify that the parent matches");
        checkArgument(
            executionPayload
                .getNumber()
                .equals(state.getLatest_execution_payload_header().getNumber().increment()),
            "process_execution_payload: Verify that the number is consequent");
      }

      checkArgument(
          executionPayload
              .getTimestamp()
              .equals(miscHelpersMerge.computeTimeAtSlot(state, state.getSlot())),
          "process_execution_payload: Verify that the timestamp is correct");

      boolean isExecutionPayloadValid =
          executionPayloadUtil.verifyExecutionStateTransition(executionPayload);

      checkArgument(
          isExecutionPayloadValid,
          "process_execution_payload: Verify that the payload is valid with respect to execution state transition");

      state.setLatestExecutionPayloadHeader(
          new ExecutionPayloadHeader(
              executionPayload.getBlock_hash(),
              executionPayload.getParent_hash(),
              executionPayload.getCoinbase(),
              executionPayload.getState_root(),
              executionPayload.getNumber(),
              executionPayload.getGas_limit(),
              executionPayload.getGas_used(),
              executionPayload.getTimestamp(),
              executionPayload.getReceipt_root(),
              executionPayload.getLogs_bloom(),
              executionPayload.getTransactions().hashTreeRoot()));

    } catch (IllegalArgumentException e) {
      LOG.warn(e.getMessage());
      throw new BlockProcessingException(e);
    }
  }

  @Override
  protected void processAttestation(
      MutableBeaconState genericState,
      Attestation attestation,
      IndexedAttestationProvider indexedAttestationProvider) {
    final MutableBeaconStateMerge state = MutableBeaconStateMerge.required(genericState);
    final AttestationData data = attestation.getData();

    PendingAttestation pendingAttestation =
        new PendingAttestation(
            attestation.getAggregation_bits(),
            data,
            state.getSlot().minus(data.getSlot()),
            UInt64.valueOf(beaconStateAccessors.getBeaconProposerIndex(state)));

    if (data.getTarget().getEpoch().equals(beaconStateAccessors.getCurrentEpoch(state))) {
      state.getCurrent_epoch_attestations().append(pendingAttestation);
    } else {
      state.getPrevious_epoch_attestations().append(pendingAttestation);
    }
  }
}