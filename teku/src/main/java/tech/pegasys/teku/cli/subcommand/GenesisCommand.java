/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.cli.subcommand;

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.MinimalEth2NetworkOptions;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.interop.InteropStartupUtil;
import tech.pegasys.teku.spec.datastructures.interop.MockStartBeaconStateGenerator;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

@Command(
    name = "genesis",
    description = "Commands for generating genesis state",
    showDefaultValues = true,
    abbreviateSynopsis = true,
    mixinStandardHelpOptions = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class GenesisCommand {

  @Command(
      name = "mock",
      description = "Generate a mock genesis state",
      mixinStandardHelpOptions = true,
      showDefaultValues = true,
      abbreviateSynopsis = true,
      versionProvider = PicoCliVersionProvider.class,
      synopsisHeading = "%n",
      descriptionHeading = "%nDescription:%n%n",
      optionListHeading = "%nOptions:%n",
      footerHeading = "%n",
      footer = "Teku is licensed under the Apache License 2.0")
  public void generate(
      @Mixin MockGenesisParams genesisParams, @Mixin MinimalEth2NetworkOptions networkOptions)
      throws IOException {
    // Output to stdout if no file is specified
    final Spec spec = networkOptions.getSpec();
    final boolean outputToFile =
        genesisParams.outputFile != null && !genesisParams.outputFile.isBlank();
    try (final OutputStream fileStream =
        outputToFile ? new FileOutputStream(genesisParams.outputFile) : System.out) {
      if (outputToFile) {
        SUB_COMMAND_LOG.generatingMockGenesis(
            genesisParams.validatorCount, genesisParams.genesisTime);
      }

      final long genesisTime = genesisParams.genesisTime;
      final List<BLSKeyPair> validatorKeys =
          new MockStartValidatorKeyPairFactory().generateKeyPairs(0, genesisParams.validatorCount);
      final BeaconState genesisState =
          InteropStartupUtil.createMockedStartInitialBeaconState(
              spec, Bytes32.fromHexString(genesisParams.eth1BlockHash), genesisTime, validatorKeys);

      if (outputToFile) {
        SUB_COMMAND_LOG.storingGenesis(genesisParams.outputFile, false);
      }
      fileStream.write(genesisState.sszSerialize().toArrayUnsafe());
      if (outputToFile) {
        SUB_COMMAND_LOG.storingGenesis(genesisParams.outputFile, true);
      }
    }
  }

  public static class MockGenesisParams {
    @Option(
        names = {"-o", "--output-file"},
        paramLabel = "<FILENAME>",
        description = "Path/filename of the output file\nDefault: stdout")
    private String outputFile = null;

    @Option(
        names = {"-v", "--validator-count"},
        paramLabel = "<VALIDATOR_COUNT>",
        description = "The number of validators to include")
    private int validatorCount = 64;

    @Option(
        names = {"-t", "--genesis-time"},
        paramLabel = "<GENESIS_TIME>",
        description = "The genesis time")
    private long genesisTime = System.currentTimeMillis() / 1000;

    @Option(
        names = {"-b", "--eth1-block-hash"},
        paramLabel = "<ETH1_BLOCK_HASH>",
        description = "The Eth1 block hash")
    private String eth1BlockHash =
        MockStartBeaconStateGenerator.INTEROP_ETH1_BLOCK_HASH.toHexString();
  }
}
