package se.kth.swim.simulation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.javatuples.Pair;

import se.kth.swim.AggregatorComp;
import se.kth.swim.HostComp;
import se.kth.swim.croupier.CroupierConfig;
import se.kth.swim.scenario.NumberNodeBuilder;
import se.sics.p2ptoolbox.simulator.cmd.OperationCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.ChangeNetworkModelCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.KillNodeCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.SimulationResult;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartAggregatorCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.core.network.NetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.DeadLinkNetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.DisconnectedNodesNetworkModel;
import se.sics.p2ptoolbox.simulator.core.network.impl.UniformRandomModel;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.GenIntSequentialDistribution;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

public class Nodes300NoKillNoDisc0 {
	private static long seed;
	private static InetAddress localHost;
	private static NumberNodeBuilder nodeBuilder;
	private static final Integer INFECTION_TIME = 60;
	private static final Integer PIGGYBACK_SIZE = 70;
	
	private static final Integer NUMBER_OF_TOTAL_NODES = 300;
	private static final Integer NUMBER_OF_NAT_NODES = 50;

	private static CroupierConfig croupierConfig = new CroupierConfig(10, 5,
			2000, 1000);
	static {
		try {
			localHost = InetAddress.getByName("127.0.0.1");
		} catch (UnknownHostException ex) {
			throw new RuntimeException(ex);
		}
	}

	static Operation1<StartAggregatorCmd, Integer> startAggregatorOp = new Operation1<StartAggregatorCmd, Integer>() {

		public StartAggregatorCmd generate(final Integer nodeId) {
			return new StartAggregatorCmd<AggregatorComp, NatedAddress>() {
				private NatedAddress aggregatorAddress;

				public Class getNodeComponentDefinition() {
					return AggregatorComp.class;
				}

				public AggregatorComp.AggregatorInit getNodeComponentInit() {
					aggregatorAddress = new BasicNatedAddress(new BasicAddress(
							localHost, 23456, nodeId));

					// Initialize here the dead nodes!
					return new AggregatorComp.AggregatorInit(aggregatorAddress,
							nodeBuilder.getSize(), new Integer[0], 5000);
				}

				public NatedAddress getAddress() {
					return aggregatorAddress;
				}

			};
		}
	};

	static Operation1<StartNodeCmd, Integer> startOpenNodeOp = new Operation1<StartNodeCmd, Integer>() {

		public StartNodeCmd generate(final Integer nodeId) {
			return new StartNodeCmd<HostComp, NatedAddress>() {
				private NatedAddress nodeAddress;

				public Class getNodeComponentDefinition() {
					return HostComp.class;
				}

				public HostComp.HostInit getNodeComponentInit(
						NatedAddress aggregatorServer,
						Set<NatedAddress> bootstrapNodes) {

					// open address
					nodeAddress = new BasicNatedAddress(new BasicAddress(
							localHost, 12345, nodeId));
					/**
					 * we don't want all nodes to start their pseudo random
					 * generators with same seed else they might behave the same
					 */
					long nodeSeed = seed + nodeId;
					return new HostComp.HostInit(nodeAddress, bootstrapNodes,
							aggregatorServer, nodeSeed, croupierConfig, INFECTION_TIME, PIGGYBACK_SIZE);
				}

				public Integer getNodeId() {
					return nodeId;
				}

				public NatedAddress getAddress() {
					return nodeAddress;
				}

				public int bootstrapSize() {
					return 5;
				}

			};
		}
	};

	static Operation1<StartNodeCmd, Integer> startNatNodeOp = new Operation1<StartNodeCmd, Integer>() {

		public StartNodeCmd generate(final Integer nodeId) {
			return new StartNodeCmd<HostComp, NatedAddress>() {
				private NatedAddress nodeAddress;

				public Class getNodeComponentDefinition() {
					return HostComp.class;
				}

				public HostComp.HostInit getNodeComponentInit(
						NatedAddress aggregatorServer,
						Set<NatedAddress> bootstrapNodes) {

					// nated address
					nodeAddress = new BasicNatedAddress(new BasicAddress(
							localHost, 12345, nodeId), NatType.NAT,
							bootstrapNodes);

					/**
					 * we don't want all nodes to start their pseudo random
					 * generators with same seed else they might behave the same
					 */
					long nodeSeed = seed + nodeId;
					return new HostComp.HostInit(nodeAddress, bootstrapNodes,
							aggregatorServer, nodeSeed, croupierConfig, INFECTION_TIME, PIGGYBACK_SIZE);
				}

				public Integer getNodeId() {
					return nodeId;
				}

				public NatedAddress getAddress() {
					return nodeAddress;
				}

				public int bootstrapSize() {
					return 5;
				}

			};
		}
	};

	static Operation1<KillNodeCmd, Integer> killNodeOp = new Operation1<KillNodeCmd, Integer>() {

		public KillNodeCmd generate(final Integer nodeId) {
			return new KillNodeCmd() {
				public Integer getNodeId() {
					return nodeId;
				}
			};
		}

	};

	static Operation<SimulationResult> simulationResult = new Operation<SimulationResult>() {

		public SimulationResult generate() {
			return new SimulationResult() {

				public void setSimulationResult(
						OperationCmd.ValidationException failureCause) {
					SwimSimulationResult.failureCause = failureCause;
				}
			};
		}
	};

	// Operations require Distributions as parameters
	// 1.ConstantDistribution - this will provide same parameter no matter how
	// many times it is called
	// 2.BasicIntSequentialDistribution - on each call it gives the next int.
	// Works more or less like a counter
	// 3.GenIntSequentialDistribution - give it a vector. It will draw elements
	// from it on each call.
	// Once out of elements it will give null.
	// So be carefull for null pointer exception if you draw more times than
	// elements
	// check se.sics.p2ptoolbox.simulator.dsl.distribution for more
	// distributions
	// you can implement your own - by extending Distribution
	public static SimulationScenario simpleBoot(final long seed) {
		Nodes300NoKillNoDisc0.seed = seed;
		nodeBuilder = new NumberNodeBuilder(NUMBER_OF_TOTAL_NODES,
				NUMBER_OF_NAT_NODES);
		SimulationScenario scen = new SimulationScenario() {
			{
				StochasticProcess startAggregator = new StochasticProcess() {
					{
						eventInterArrivalTime(constant(1000));
						raise(1, startAggregatorOp, new ConstantDistribution(
								Integer.class, 0));
					}
				};

				StochasticProcess startPeers = new StochasticProcess() {
					{
						eventInterArrivalTime(constant(1000));
						// raise(10, startNodeOp, new
						// GenIntSequentialDistribution(
						// new Integer[] {2, 3, 4, 5, 6, 7, 8, 9, 10, 11}));
						raise(nodeBuilder.getOpenNodes().size(),
								startOpenNodeOp,
								new GenIntSequentialDistribution(
										nodeBuilder.getOpenNodes()
												.toArray(
														new Integer[nodeBuilder
																.getOpenNodes()
																.size()])));

						if (nodeBuilder.getNatedNodes().size() > 0) {
							raise(nodeBuilder.getNatedNodes().size(),startNatNodeOp,
									new GenIntSequentialDistribution(nodeBuilder.getNatedNodes()
													.toArray(new Integer[nodeBuilder
													          .getNatedNodes().size()])));
						}
					}
				};

				StochasticProcess fetchSimulationResult = new StochasticProcess() {
					{
						eventInterArrivalTime(constant(1000));
						raise(1, simulationResult);
					}
				};

				startAggregator.start();
				startPeers.startAfterTerminationOf(1000, startAggregator);
				fetchSimulationResult.startAfterTerminationOf(30 * 1000,
						startPeers);
				terminateAfterTerminationOf(10000000, fetchSimulationResult);

			}
		};

		scen.setSeed(seed);

		return scen;
	}
}
