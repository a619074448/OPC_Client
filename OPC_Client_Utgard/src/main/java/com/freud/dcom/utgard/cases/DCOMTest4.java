package com.freud.dcom.utgard.cases;

import static com.freud.opc.utgard.BaseConfiguration.CONFIG_CLSID;
import static com.freud.opc.utgard.BaseConfiguration.CONFIG_DOMAIN;
import static com.freud.opc.utgard.BaseConfiguration.CONFIG_HOST;
import static com.freud.opc.utgard.BaseConfiguration.CONFIG_PASSWORD;
import static com.freud.opc.utgard.BaseConfiguration.CONFIG_USERNAME;
import static com.freud.opc.utgard.BaseConfiguration.getEntryValue;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.common.JISystem;
import org.jinterop.dcom.core.IJIComObject;
import org.jinterop.dcom.core.JIClsid;
import org.jinterop.dcom.core.JIComServer;
import org.jinterop.dcom.core.JISession;
import org.openscada.opc.dcom.common.EventHandler;
import org.openscada.opc.dcom.common.KeyedResult;
import org.openscada.opc.dcom.common.KeyedResultSet;
import org.openscada.opc.dcom.common.Result;
import org.openscada.opc.dcom.common.ResultSet;
import org.openscada.opc.dcom.common.impl.OPCCommon;
import org.openscada.opc.dcom.da.OPCDATASOURCE;
import org.openscada.opc.dcom.da.OPCITEMDEF;
import org.openscada.opc.dcom.da.OPCITEMRESULT;
import org.openscada.opc.dcom.da.OPCITEMSTATE;
import org.openscada.opc.dcom.da.impl.OPCGroupStateMgt;
import org.openscada.opc.dcom.da.impl.OPCItemMgt;
import org.openscada.opc.dcom.da.impl.OPCServer;
import org.openscada.opc.dcom.da.impl.OPCSyncIO;

/**
 * 同步读取Item
 * 
 * @author Freud
 * 
 */
public class DCOMTest4 {

	public static void main(String[] args) throws Exception {
		JISystem.setAutoRegisteration(true);

		/**
		 * Session获取
		 */
		JISession _session = JISession.createSession(
				getEntryValue(CONFIG_DOMAIN), getEntryValue(CONFIG_USERNAME),
				getEntryValue(CONFIG_PASSWORD));

		final JIComServer comServer = new JIComServer(
				JIClsid.valueOf(getEntryValue(CONFIG_CLSID)),
				getEntryValue(CONFIG_HOST), _session);

		final IJIComObject serverObject = comServer.createInstance();

		OPCServer server = new OPCServer(serverObject);

		/**
		 * 添加一个Group的信息
		 */
		OPCGroupStateMgt group = server.addGroup("test", true, 100, 1234, 60,
				0.0f, 1033);

		testItems(server, group, new String[] { "Saw-toothed Waves.Int2",
				"Saw-toothed Waves.test2" });

		// clean up
		server.removeGroup(group, true);
	}

	private static void showError(final OPCCommon common, final int errorCode)
			throws JIException {
		System.out.println(String.format("Error (%X): '%s'", errorCode,
				common.getErrorString(errorCode, 1033)));
	}

	private static void showError(final OPCServer server, final int errorCode)
			throws JIException {
		showError(server.getCommon(), errorCode);
	}

	private static boolean dumpOPCITEMRESULT(
			final KeyedResultSet<OPCITEMDEF, OPCITEMRESULT> result) {
		int failed = 0;
		for (final KeyedResult<OPCITEMDEF, OPCITEMRESULT> resultEntry : result) {
			System.out.println("==================================");
			System.out.println(String.format("Item: '%s' ", resultEntry
					.getKey().getItemID()));

			System.out.println(String.format("Error Code: %08x",
					resultEntry.getErrorCode()));
			if (!resultEntry.isFailed()) {
				System.out.println(String.format("Server Handle: %08X",
						resultEntry.getValue().getServerHandle()));
				System.out.println(String.format("Data Type: %d", resultEntry
						.getValue().getCanonicalDataType()));
				System.out.println(String.format("Access Rights: %d",
						resultEntry.getValue().getAccessRights()));
				System.out.println(String.format("Reserved: %d", resultEntry
						.getValue().getReserved()));
			} else {
				failed++;
			}
		}
		return failed == 0;
	}

	private static void testItems(final OPCServer server,
			final OPCGroupStateMgt group, final String... itemIDs)
			throws IllegalArgumentException, UnknownHostException, JIException {

		final OPCItemMgt itemManagement = group.getItemManagement();
		final List<OPCITEMDEF> items = new ArrayList<OPCITEMDEF>(itemIDs.length);
		for (final String id : itemIDs) {
			final OPCITEMDEF item = new OPCITEMDEF();
			item.setItemID(id);
			item.setClientHandle(new Random().nextInt());
			items.add(item);
		}

		final OPCITEMDEF[] itemArray = items.toArray(new OPCITEMDEF[0]);

		System.out.println("Validate");
		KeyedResultSet<OPCITEMDEF, OPCITEMRESULT> result = itemManagement
				.validate(itemArray);
		if (!dumpOPCITEMRESULT(result)) {
			return;
		}

		// now add them to the group
		System.out.println("Add");
		result = itemManagement.add(itemArray);
		if (!dumpOPCITEMRESULT(result)) {
			return;
		}

		// get the server handle array
		final Integer[] serverHandles = new Integer[itemArray.length];
		for (int i = 0; i < itemArray.length; i++) {
			serverHandles[i] = new Integer(result.get(i).getValue()
					.getServerHandle());
		}

		// set them active
		System.out.println("Activate");
		final ResultSet<Integer> resultSet = itemManagement.setActiveState(
				true, serverHandles);
		for (final Result<Integer> resultEntry : resultSet) {
			System.out.println(String.format("Item: %08X, Error: %08X",
					resultEntry.getValue(), resultEntry.getErrorCode()));
		}

		// set client handles
		System.out.println("Set client handles");
		final Integer[] clientHandles = new Integer[serverHandles.length];
		for (int i = 0; i < serverHandles.length; i++) {
			clientHandles[i] = i;
		}
		itemManagement.setClientHandles(serverHandles, clientHandles);

		System.out.println("Create async IO 2.0 object");
		// OPCAsyncIO2 asyncIO2 = group.getAsyncIO2 ();
		// connect handler

		System.out.println("attach");
		final EventHandler eventHandler = group.attach(new DumpDataCallback());

		// sleep
		try {
			System.out.println("Waiting...");
			Thread.sleep(10 * 1000);
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}

		eventHandler.detach();

		// sync IO - read
		final OPCSyncIO syncIO = group.getSyncIO();
		// OPCAsyncIO2 asyncIO2 = group.getAsyncIO2 ();
		/*
		 * System.out.println ( "attach..enable" ); asyncIO2.setEnable ( true );
		 * System.out.println ( "attach..refresh" ); asyncIO2.refresh (
		 * (short)1, 1 );
		 */

		final KeyedResultSet<Integer, OPCITEMSTATE> itemState = syncIO.read(
				OPCDATASOURCE.OPC_DS_DEVICE, serverHandles);
		for (final KeyedResult<Integer, OPCITEMSTATE> itemStateEntry : itemState) {
			final int errorCode = itemStateEntry.getErrorCode();
			System.out
					.println(String
							.format("Server ID: %08X, Value: %s, Timestamp: %d/%d (%Tc), Quality: %d, Error: %08X",
									itemStateEntry.getKey(), itemStateEntry
											.getValue().getValue(),
									itemStateEntry.getValue().getTimestamp()
											.getHigh(),
									itemStateEntry.getValue().getTimestamp()
											.getLow(), itemStateEntry
											.getValue().getTimestamp()
											.asCalendar(), itemStateEntry
											.getValue().getQuality(), errorCode));
			if (errorCode != 0) {
				showError(server, errorCode);
			}
		}

		// set them inactive
		System.out.println("In-Active");
		itemManagement.setActiveState(false, serverHandles);

		// finally remove them again
		System.out.println("Remove");
		itemManagement.remove(serverHandles);
	}

}
