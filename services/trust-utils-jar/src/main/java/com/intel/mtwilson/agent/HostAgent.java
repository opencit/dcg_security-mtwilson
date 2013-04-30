/*
 * Copyright (C) 2012 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.agent;

import com.intel.mtwilson.datatypes.TxtHostRecord;
import com.intel.mtwilson.model.Aik;
import com.intel.mtwilson.model.Nonce;
import com.intel.mtwilson.model.PcrIndex;
import com.intel.mtwilson.model.PcrManifest;
import com.intel.mtwilson.model.TpmQuote;
import java.io.IOException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;

/**
 * XXX TODO this is a draft of the interface that Linux, Citrix, and Vmware
 * agents should implement for communicating information about their hosts
 * to Mt Wilson. THis will allow Mt Wilson to treat them uniformly and move
 * all the platform-specific calls and procedures into those agents in a 
 * clean way. 
 * 
 * To obtain a HostAgent object, use the HostAgentFactory to create one for a 
 * given host. All the methods in this interface apply to the given host.
 * 
 * All the methods in this interface are intended to retrieve information
 * from the host (or its master/manager server). 
 * 
 * Note that the HostAgent is not responsible for interpreting the attestation.
 * It is only responsible for obtaining the host information, AIK, TPM Quote,
 * and Module Manifest. The Attestation Service will interpret these.
 * 
 * The methods isTpmPresent(), isTpmEnabled(), isIntelTxtSupported(), and
 *  isIntelTxtEnabled() help the
 * attestation service provide detailed trust status to clients:
 * 
 * 1. Trusted  (host complies with assigned policies in whitelist)
 * 2. Untrusted (host does not comply with assigned policies in whitelist)
 * 3. Intel TXT Not Enabled (when isIntelTxtEnabled()==false)
 * 4. TPM Not Enabled (when isTpmEnabled()==false)
 * 5. TXT and TPM Not Enabled (when both isTpmEnabled()==false and isIntelTxtEnabled()==false)
 * 6. TXT Not Supported (when isIntelTxtSupported()==false)
 * 
 * if isIntelTxtSupported() and isIntelTxtEnabled() and isTpmPresent() and isTpmEnabled() then
 *      ... evaluate policies to determine trusted or untrusted ...
 *      ... actually isIntelTxtSupported() is implied by isIntelTxtEnabled() ...
 *      ... and isTpmPresent() is implied by isTpmEnabled() ...
 * else
 *      ... host trust status is unknown ...
 *      if isIntelTxtSupported() == false then display #6
 *      else if isIntelTxtEnabled() == false && isTpmEnabled() == false then display #5
 *      else if isIntelTxtEnabled() == false then display #3
 *      else if isTpmEnabled() == false then display #4
 *      
 * 
 * 
 * @author jbuhacoff
 */
public interface HostAgent {

    /**
     * Whether the platform supports Intel TXT - is the right hardware present (not including the TPM)
     * XXX for now all hosts return true until we implement all the detection mechanism
     * @return 
     */
    boolean isIntelTxtSupported();
    
    /**
     * Whether Intel TXT  has been enabled on the platform (usually through the BIOS)
     * XXX for now all hosts return true until we implement all the detection mechanism
     * @return 
     */
    boolean isIntelTxtEnabled();
    
    
    /**
     * XXX for now probably all hosts will return true, until we implement all the detection mechanisms
     * Available means the TPM hardware is present.
     * Linux, Citrix, and Vmware agents should contact the host and find out
     * if it has a TPM before determining the return value.
     * @return true if the host has a TPM
     */
    boolean isTpmPresent();
    
    /**
     * XXX for now probably all hosts will return true, until we implement all the detection mechanisms
     * Linux, Citrix agents should contact the host and find out
     * if its TPM is enabled (BIOS enabled and also if the agents have ownership).
     * In this case, "enabled" means it has an owner set AND that owner is
     * cooperating with Mt Wilson. 
     * Vmware agents can return true if isTpmAvailable() returns true.
     * @return 
     */
    boolean isTpmEnabled();

    
    /**
     * Linux and Citrix agents should return true, Vmware should return false.
     * @return true if we can obtain the EK for the host
     */
    boolean isEkAvailable();
    
    X509Certificate getEkCertificate();
    
    
    /**
     * Linux and Citrix agents should return true, Vmware should return false.
     * @return true if we can obtain am AIK for the host.
     */
    boolean isAikAvailable();
    
    /**
     * AIK's are RSA public keys.  The certificates only exist when a Privacy CA or
     * a Mt Wilson CA signs the public key to create a certificate.
     * @return 
     */
    PublicKey getAik();
    
    /**
     * Linux agent should return true because we use the Privacy CA.
     * Citrix agent uses DAA so it should return false.
     * Vmware agent should return false.
     * @return 
     */
    boolean isAikCaAvailable();
    
    /**
     * XXX draft - maybe it should return an X509Certificate object
     * @return 
     */
    X509Certificate getAikCertificate();
    
    /**
     * XXX draft - maybe it should return an X509Certificate object
     * @return the Privacy CA certificate that is mentioned in the AIK Certificate
     */
    X509Certificate getAikCaCertificate(); 

    
    /**
     * Linux and Vmware agent should return false.
     * Citrix agent should return true.
     * @return true if the host supports Direct Anonymous Attestation
     */
    boolean isDaaAvailable();
    
    
    
    /**
     * XXX draft to approximate getting the bios/os/vmm details from the host...
     * maybe split it up into those three functions? or return a host information
     * object with those details? should be similar to or the same as the host portion of the mle 
     * object ?
     * @return 
     */
    String getHostInformation();
    
    
    /**
     * Every vendor has a different API for obtaining the TPM Quote, module
     * information, etc. 
     * An administrator may want to log the "raw output" from the vendor before
     * parsing and validating. 
     * For Vmware, it's an XML document with their externally-unverifiable report
     * on the host. For Citrix and Intel, it's an XML document containing the TPM Quote and
     * other information. 
     * @return 
     */
    String getVendorHostReport()  throws IOException;
    
    /**
     * XXX this is a draft - need to check it against linux & citrix requirements
     * to ensure it makes sense. 
     * Vmware agent must throw unsupported operation exception since it doesn't
     * provide quotes, only "pcr information" through it's API. 
     * @param aik
     * @param nonce
     * @param pcr
     * @return 
     */
    TpmQuote getTpmQuote(Aik aik, Nonce nonce, Set<PcrIndex> pcr);
    
    
    
    /**
     * 
     * Agents should return the entire set of PCRs from the host. The attestation
     * service will then choose the ones it wants to verify against the whitelist.
     * Returning all PCR's is cheap (there are only 24) and makes the API simple.
     * 
     * Agents should return the entire set of module measurements from the host.
     * The attestation service will then choose what to verify and how. 
     * 
     * XXX TODO this method is moved here from the previous interface ManifestStrategy.
     * It's currently here to minimize code changes for the current release
     * but its functionality needs to be moved to the other HostAgent methods.
     * The VCenterHost was written with abstract methods for processDigest() and
     * processReport() and these were overridden "on the fly" with anonymous
     * subclasses in two places.  No time right now to rewrite it properly but
     * they are essentially post-processing the results we obtain from vcenter.
     * So in this adapted getManifest() method, we just provide the subclass
     * instance so it can be called for the post-processing.
     * 
     * Bug #607 changed return type to PcrManifest and removed post-processing argument - 
     * each host agent implementation is reponsible for completing all its processing.
     * @param host
     * @return 
     */
    PcrManifest getPcrManifest() throws IOException;
    
    /**
     * XXX TODO adapter for existing interface
     * 
     * SAMPLE OUTPUT FROM VMWare Host:
     * BIOS - OEM:Intel Corporation
     * BIOS - Version:S5500.86B.01.00.0060.090920111354
     * OS Name:VMware ESXi
     * OS Version:5.1.0
     * VMM Name: VMware ESXi
     * VMM Version:5.1.0-613838 (Build Number)
     * 
     */
    TxtHostRecord getHostDetails() throws IOException; // original interface passed TxtHostRecord even though all the method REALLY needs is the connection string (hostname and url for vcenter,  ip adderss and port for intel but can be in the form of a connection string);  but since the hostagent interface is for a host already selected... we don't need any arguments here!!    the IOException is to wrap any client-specific error, could be changed to be soemthing more specific to trust utils library 


    /**
     * Another adapter for existing code.  Each vendor returns a string in their own format.
     * @param pcrList  may be ignored, and the full list returned
     * @return
     * @throws IOException 
     */
    String getHostAttestationReport(String pcrList) throws IOException;
    
    
    /**
     * Use this to obtain host-specific information such as UUID, which may be 
     * needed for dynamic whitelist rules.  Attributes returned with this method
     * may be referenced by name from dynamic whitelist rules.
     * @return
     * @throws IOException 
     */
    Map<String,String> getHostAttributes() throws IOException;
}