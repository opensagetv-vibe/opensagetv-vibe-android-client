from pathlib import Path
import importlib.util,unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('experience_probe',ROOT/'scripts/mcp_playback_experience.py')
probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe)

class ExperienceContract(unittest.TestCase):
    def trace(self):
        return dict(schemaVersion=1,active=True,sourceGeneration=1,seekSequence=2,
                    requestedPositionMs=42000,clientAcceptedPositionMs=42000,
                    producerAcceptedPositionMs=42000,producerPrepareMs=500,
                    presentedPositionMs=None,stockUiProcessedAtMs=None,
                    events=[dict(phase='seek-requested',monotonicMs=1,operationSequence=2,positionMs=42000),
                            dict(phase='client-operation-accepted',monotonicMs=1,operationSequence=2,positionMs=42000)])
    def test_preserves_unknown_presentation_and_closed_history(self):
        value=self.trace();value['privateUrl']='must not export'
        result=probe.closed_trace(value);probe.require_seek(result,42000,1,True)
        self.assertNotIn('privateUrl',result)
        self.assertIsNone(result['presentedPositionMs'])
    def test_real_enum_operation_names_include_underscores(self):
        value=self.trace();value['events'].append(dict(phase='operation-surface_replacement',monotonicMs=2,operationSequence=3,positionMs=None))
        self.assertEqual('operation-surface_replacement',probe.closed_trace(value)['events'][-1]['phase'])
    def test_unattributed_callbacks_do_not_certify_landing(self):
        value=self.trace();value['presentedPositionMs']=42000
        with self.assertRaises(AssertionError):probe.require_seek(probe.closed_trace(value),42000,1,True)
    def test_native_path_never_claims_owned_producer(self):
        value=self.trace();value['producerAcceptedPositionMs']=None
        probe.require_seek(probe.closed_trace(value),42000,1,False)
        with self.assertRaises(AssertionError):probe.require_seek(probe.closed_trace(value),42000,1,True)
    def test_stale_generation_and_private_event_values_refused(self):
        value=self.trace()
        with self.assertRaises(AssertionError):probe.require_seek(probe.closed_trace(value),42000,2,True)
        value['events'][0]['phase']='http://private'
        with self.assertRaises(ValueError):probe.closed_trace(value)
    def test_history_and_integer_contract_bounded(self):
        value=self.trace();value['events']*=33
        with self.assertRaises(ValueError):probe.closed_trace(value)
        value=self.trace();value['producerPrepareMs']='unknown'
        with self.assertRaises(ValueError):probe.closed_trace(value)

if __name__=='__main__':unittest.main()
