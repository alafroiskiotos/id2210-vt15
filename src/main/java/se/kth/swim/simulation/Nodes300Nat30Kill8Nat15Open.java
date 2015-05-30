package se.kth.swim.simulation;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import se.kth.swim.AggregatorComp;
import se.kth.swim.HostComp;
import se.kth.swim.croupier.CroupierConfig;
import se.kth.swim.scenario.NumberNodeBuilder;
import se.sics.p2ptoolbox.simulator.cmd.OperationCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.KillNodeCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.SimulationResult;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartAggregatorCmd;
import se.sics.p2ptoolbox.simulator.cmd.impl.StartNodeCmd;
import se.sics.p2ptoolbox.simulator.dsl.SimulationScenario;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation;
import se.sics.p2ptoolbox.simulator.dsl.adaptor.Operation1;
import se.sics.p2ptoolbox.simulator.dsl.distribution.ConstantDistribution;
import se.sics.p2ptoolbox.simulator.dsl.distribution.extra.GenIntSequentialDistribution;
import se.sics.p2ptoolbox.util.network.NatType;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

public class Nodes300Nat30Kill8Nat15Open {
	private static long seed;
	private static InetAddress localHost;
	private static NumberNodeBuilder nodeBuilder;
	
	private static final Integer INFECTION_TIME = 120;
	private static final Integer PIGGYBACK_SIZE = 80;
	
	private static final Integer NUMBER_OF_TOTAL_NODES = 130;
	private static final Integer NUMBER_OF_NAT_NODES = 30;
	private static final Integer NUMBER_OF_NAT_KILL = 8;
	private static final Integer NUMBER_OF_OPEN_KILL = 15;
	
	private static Integer[] concatKillId;

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
							nodeBuilder.getSize(), concatKillId, 5000);
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
	
	public static SimulationScenario scenario(final long seed) {
		Nodes300Nat30Kill8Nat15Open.seed = seed;
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
				
				StochasticProcess killPeers = new StochasticProcess() {
					{
						eventInterArrivalTime(constant(1000));
						Integer[] openIdToKill = nodeBuilder.getOpenNodes().subList(40, 40 + NUMBER_OF_OPEN_KILL + 1).toArray(new Integer[NUMBER_OF_OPEN_KILL]);
						Integer[] natIdToKill = nodeBuilder.getNatedNodes().subList(0, NUMBER_OF_NAT_KILL + 1).toArray(new Integer[NUMBER_OF_NAT_KILL]);

						raise(NUMBER_OF_OPEN_KILL, killNodeOp, new GenIntSequentialDistribution(openIdToKill));
						raise(NUMBER_OF_NAT_KILL, killNodeOp, new GenIntSequentialDistribution(natIdToKill));
						
						concatKillId = new Integer[NUMBER_OF_OPEN_KILL + NUMBER_OF_NAT_KILL];
						
						System.arraycopy(openIdToKill, 0, concatKillId, 0, NUMBER_OF_OPEN_KILL);
						System.arraycopy(natIdToKill, 0, concatKillId, NUMBER_OF_OPEN_KILL, NUMBER_OF_NAT_KILL);
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
				killPeers.startAfterTerminationOf(3000, startPeers);
				fetchSimulationResult.startAfterTerminationOf(700 * 1000,
						startPeers);
				terminateAfterTerminationOf(8000000, fetchSimulationResult);

			}
		};

		scen.setSeed(seed);

		return scen;
	}
}
