import {Component, Inject} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialogRef} from '@angular/material/dialog';
import {
    AvsTaskDescription,
    CollectionService,
    KisTextualTaskDescription,
    KisVisualTaskDescription,
    MediaCollection,
    MediaItem,
    TaskDescriptionBase,
    TaskGroup,
    TemporalPoint,
    TemporalRange,
    VideoItem
} from '../../../../openapi';
import {FormArray, FormControl, FormGroup, Validators} from '@angular/forms';
import {Observable} from 'rxjs';
import {filter, flatMap} from 'rxjs/operators';


export interface CompetitionBuilderTaskDialogData {
    taskGroup: TaskGroup;
    task?: TaskDescriptionBase;
}

@Component({
    selector: 'app-competition-builder-task-dialog',
    templateUrl: './competition-builder-task-dialog.component.html'
})
export class CompetitionBuilderTaskDialogComponent {


    form: FormGroup;
    units = ['FRAME_NUMBER', 'SECONDS', 'MILLISECONDS', 'TIMECODE'];
    mediaCollectionSource: Observable<MediaCollection[]>;
    mediaItemSource: Observable<MediaItem[]>;

    constructor(public dialogRef: MatDialogRef<CompetitionBuilderTaskDialogComponent>,
                public collectionService: CollectionService,
                @Inject(MAT_DIALOG_DATA) public data: CompetitionBuilderTaskDialogData) {

        this.mediaCollectionSource = this.collectionService.getApiCollection();

        switch (this.data.taskGroup.type) {
            case 'KIS_VISUAL':
                this.form = CompetitionBuilderTaskDialogComponent.KisVisualFormControl(this.data.taskGroup, this.data.task as KisVisualTaskDescription);
                this.mediaItemSource = this.form.get('mediaItemId').valueChanges.pipe(
                    filter((value: string) => value.length >= 3),
                    flatMap(value => {
                        return this.collectionService.getApiCollectionWithCollectionidWithStartswith(this.form.get('mediaCollection').value, value);
                    })
                );
                break;
            case 'KIS_TEXTUAL':
                this.form = CompetitionBuilderTaskDialogComponent.KisTextualFormControl(this.data.taskGroup, this.data.task as KisTextualTaskDescription);
                this.mediaItemSource = this.form.get('mediaItemId').valueChanges.pipe(
                    filter((value: string) => value.length >= 3),
                    flatMap((value) => {
                        return this.collectionService.getApiCollectionWithCollectionidWithStartswith(this.form.get('mediaCollection').value, value);
                    })
                );
                break;
            case 'AVS':
                this.form = CompetitionBuilderTaskDialogComponent.AvsFormControl(this.data.taskGroup, this.data.task as AvsTaskDescription);
                break;
        }
    }

    public static BasicFormControl(taskGroup: TaskGroup, task?: TaskDescriptionBase) {
        return new FormGroup({
            name: new FormControl(task?.name, Validators.required),
            duration: new FormControl(task?.duration ? task.duration : taskGroup?.defaultTaskDuration, [Validators.required, Validators.min(5)])
        });
    }

    /**
     * Prepares and initializes the FormControl for an KIS Textual Task Description.
     *
     * @param taskGroup The task group the new task should belong to.
     * @param task The task item (optional)
     */
    public static KisVisualFormControl(taskGroup: TaskGroup, task?: KisVisualTaskDescription) {
        const addTo = this.BasicFormControl(taskGroup, task);

        addTo.addControl('mediaCollection', new FormControl(task?.item.collection, [Validators.required]));
        addTo.addControl('mediaItemId', new FormControl(task?.item, [Validators.required, Validators.min(1)]));
        addTo.addControl('start', new FormControl(task?.temporalRange.start.value, [Validators.required, Validators.min(0)]));
        addTo.addControl('end', new FormControl(task?.temporalRange.end.value, [Validators.required, Validators.min(0)]));
        addTo.addControl('time_unit', new FormControl(task?.temporalRange.start.unit ? task.temporalRange.start.unit : 'FRAME_NUMBER', [Validators.required, Validators.min(0)]));
        return addTo;
    }

    /**
     * Prepares and initializes the FormControl for an KIS Textual Task Description.
     *
     * @param taskGroup The task group the new task should belong to.
     * @param task The task item (optional)
     */
    public static KisTextualFormControl(taskGroup: TaskGroup, task?: KisTextualTaskDescription) {
        const addTo = this.BasicFormControl(taskGroup, task);
        addTo.addControl('mediaCollection', new FormControl(task?.item.collection, [Validators.required]));
        addTo.addControl('mediaItemId', new FormControl(task?.item, [Validators.required, Validators.min(1)]));
        addTo.addControl('start', new FormControl(task?.temporalRange.start.value, [Validators.required, Validators.min(0)]));
        addTo.addControl('end', new FormControl(task?.temporalRange.end.value, [Validators.required, Validators.min(0)]));
        addTo.addControl('time_unit', new FormControl(task?.temporalRange.start.unit ? task.temporalRange.start.unit : 'FRAME_NUMBER', [Validators.required, Validators.min(0)]));
        if (task != null) {
            addTo.addControl('descriptions', new FormArray(task.descriptions.map((v) => new FormControl(v, [Validators.minLength(1), Validators.required]))));
        } else {
            addTo.addControl('descriptions', new FormArray([new FormControl('', [Validators.minLength(1), Validators.required])]));
        }
        addTo.addControl('delay', new FormControl(task?.delay ? task.delay : 30, [Validators.required, Validators.min(0)]));
        return addTo;
    }

    /**
     * Prepares and initializes the FormControl for an AVS Task Description.
     *
     * @param taskGroup The task group the new task should belong to.
     * @param task The task item (optional)
     */
    public static AvsFormControl(taskGroup: TaskGroup, task?: AvsTaskDescription) {
        const addTo = this.BasicFormControl(taskGroup, task);
        addTo.addControl('mediaCollection', new FormControl(task?.defaultCollection));
        addTo.addControl('description', new FormControl(task?.description, Validators.minLength(1)));
        return addTo;
    }

    /**
     * Handler for + button for task descriptions (KIS_TEXTUAL tasks only). Adds a description.
     *
     * @param index The index to add description at.
     */
    public addDescription(index: number) {
        (this.form.get('descriptions') as FormArray).insert(index, new FormControl('', Validators.minLength(1)));
    }

    /**
     * Handler for (-) button for task descriptions (KIS_TEXTUAL tasks only). Removes a description.
     *
     * @param index The index to remove description at.
     */
    public removeDescription(index: number) {
        (this.form.get('descriptions') as FormArray).removeAt(index);
    }

    /**
     * Converts a MediaItem to its display value for the autocomplete field.
     *
     * @param value MediaItem to convert
     */
    public mediaItemToDisplay(value: MediaItem) {
        if (value) {
            return `${value.name} (${value.id})`;
        } else {
            return '';
        }
    }

    /**
     * Handler for 'save' button.
     */
    public save() {
        if (this.form.valid) {
            this.dialogRef.close(this.getTaskDescription());
        }
    }

    randomisedMediaItem() {
        this.collectionService.getApiCollectionRandomWithCollectionid(
            (this.form.get('mediaCollection') as FormControl).value as number)
            .subscribe(value => {
                this.form.get('mediaItemId').setValue(value);
            });
    }

    randomiseSegment() {
        // TODO rework with #122
        const item = this.form.get('mediaItemId').value as VideoItem;
        const start = this.randInt(1, (item.durationMs / 1000) / 2); // always in first half
        let end = 1;
        do {
            end = start + this.randInt(5, (item.durationMs / 1000)); // Arbitrary 5 seconds minimal length
        } while (end > (item.durationMs / 1000));
        this.form.get('time_unit').setValue('SECONDS');
        this.form.get('start').setValue(start);
        this.form.get('end').setValue(end);
    }

    /**
     * Handler for 'close' button.
     */
    public close(): void {
        this.dialogRef.close(null);
    }

    private rand(min: number, max: number): number {
        return Math.random() * (max - min) + min;
    }

    private randInt(min: number, max: number): number {
        min = Math.floor(min);
        max = Math.ceil(max);
        return Math.round(Math.random() * (max - min + 1) + min);
    }

    private getTaskDescription(): TaskDescriptionBase {
        switch (this.data.taskGroup.type) {
            case 'AVS':
                return {
                    name: this.form.get('name').value,
                    taskType: this.data.taskGroup.type,
                    taskGroup: this.data.taskGroup,
                    duration: this.form.get('duration').value,
                    defaultCollection: this.form.get('mediaCollection').value,
                    description: this.form.get('description').value
                } as AvsTaskDescription;
            case 'KIS_TEXTUAL':
                return {
                    name: this.form.get('name').value,
                    taskType: this.data.taskGroup.type,
                    taskGroup: this.data.taskGroup,
                    duration: this.form.get('duration').value,
                    item: (this.form.get('mediaItemId').value) as VideoItem,
                    temporalRange: {
                        start: {
                            value: this.form.get('start').value,
                            unit: this.form.get('time_unit').value
                        } as TemporalPoint,
                        end: {
                            value: this.form.get('end').value,
                            unit: this.form.get('time_unit').value
                        } as TemporalPoint
                    } as TemporalRange,
                    descriptions: (this.form.get('descriptions') as FormArray).value,
                    delay: this.form.get('delay').value
                } as KisTextualTaskDescription;
            case 'KIS_VISUAL':
                return {
                    name: this.form.get('name').value,
                    taskType: this.data.taskGroup.type,
                    taskGroup: this.data.taskGroup,
                    duration: this.form.get('duration').value,
                    item: (this.form.get('mediaItemId').value) as VideoItem,
                    temporalRange: {
                        start: {
                            value: this.form.get('start').value,
                            unit: this.form.get('time_unit').value
                        } as TemporalPoint,
                        end: {
                            value: this.form.get('end').value,
                            unit: this.form.get('time_unit').value
                        } as TemporalPoint
                    } as TemporalRange,
                } as KisVisualTaskDescription;
        }
    }
}
