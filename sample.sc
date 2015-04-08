s.makeGui

 (
 SynthDef("dynenv", {arg val = 0, dur = 1, ch = 0;
 	Out.ar(ch, DynEnv.ar(val, dur));
 }).add
 )

(
 var controlGroup = Group.new;
 var soundGroup = Group.new;

 var rotateBus = Bus.audio(s, 1);
 var accBus = Bus.audio(s, 1);

 var acc = Synth("dynenv", [\ch, accBus], controlGroup);
 var rotate = Synth("dynenv", [\ch, rotateBus], controlGroup);
 acc.set(\dur, 1/(60 - 2));
 rotate.set(\dur, 1/(60 - 2));

 soundGroup.moveAfter(controlGroup);

 OSCFunc({|msg, time, addr, recvPort|
		var accX = msg[1];
		var accY = msg[2];
		var accZ = msg[3];
		var rotateX = msg[4];
		var rotateY = msg[5];
		var rotateZ = msg[6];
		acc.set(\val, accX);
		rotate.set(\val, rotateX);
 }, '/Data', nil,1235);


/*
{PMOsc.ar(
	440 * (In.ar(rotateBus) + 1).poll, 
	10 * In.ar(accBus).abs.scope, 3)}.play(soundGroup);
*/

// http://en.wikibooks.org/wiki/Designing_Sound_in_SuperCollider/Creaking_door

~woodfilter = { |input|
	var freqs, rqs, output;
	freqs = [62.5, 125, 250, 395, 560, 790];
	rqs   = 1 / [1, 1, 2, 2, 3, 3];
 
	output = BPF.ar(input, freqs, rqs).sum + (input*0.2);
 
};

~stickslip = { |force|
	var inMotion, slipEvents, forceBuildup, evtAmp, evtDecayTime, evts;
	force = force.lag(0.1); // smoothing to get rid of volatile control changes
	inMotion = force > 0.1; // static friction: nothing at all below a certain force
	slipEvents = inMotion * Impulse.ar(force.linlin(0.1, 1, 1, 1/0.003) * LFDNoise1.ar(50).squared.linexp(-1,1, 0.5, 2));
 
	forceBuildup = Phasor.ar(slipEvents, 10 * SampleDur.ir, 0, inf).min(1);
 
	evtAmp = Latch.ar(Delay1.ar(forceBuildup.sqrt), slipEvents);
	evtDecayTime = evtAmp.sqrt;
	evts = EnvGen.ar(Env.perc(0.001, 1), slipEvents, evtAmp, 0, evtDecayTime * 0.01);
};
~squarepanel = { |input|
	var times, filt;
	times = [4.52, 5.06, 6.27, 8, 5.48, 7.14, 10.12, 16] * 0.001;
	filt = DelayC.ar(input, times, times).mean;
	filt = HPF.ar(filt, 125);
	filt * 4
};

{~squarepanel.value(~woodfilter.value(~stickslip.value(
	LinLin.ar(In.ar(rotateBus).poll,0, 0.1, 0, 1))))}.play(soundGroup)
//	LinLin.ar(In.ar(rotateBus).poll,0, 1, 0, 1))))}.play(soundGroup)
)