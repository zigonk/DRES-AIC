import { AfterViewInit, Component, ViewChild } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { UserDetails, UserRequest, UserService } from '../../../../openapi';
import { MatDialog } from '@angular/material/dialog';
import { AdminUserCreateOrEditDialogComponent } from '../admin-user-create-or-edit-dialog/admin-user-create-or-edit-dialog.component';
import { filter, flatMap } from 'rxjs/operators';
import { MatSort, Sort } from '@angular/material/sort';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { MatTableDataSource } from '@angular/material/table';

@Component({
  selector: 'app-admin-user-list',
  templateUrl: './admin-user-list.component.html',
  styleUrls: ['./admin-user-list.component.scss'],
})
export class AdminUserListComponent implements AfterViewInit {
  // TODO Add Team info / link

  displayColumns = ['actions', 'id', 'name', 'role'];

  @ViewChild(MatSort) sort: MatSort;
  dataSource = new MatTableDataSource<UserDetails>([]);

  isFilterInputActive = false;
  filterValue = '';

  constructor(
    private snackBar: MatSnackBar,
    private userService: UserService,
    private dialog: MatDialog,
    private liveAnnouncer: LiveAnnouncer
  ) {}

  public create() {
    const dialogRef = this.dialog.open(AdminUserCreateOrEditDialogComponent, { width: '500px' });
    dialogRef
      .afterClosed()
      .pipe(
        filter((r) => r != null),
        flatMap((u: UserRequest) => {
          return this.userService.postApiV1User(u);
        })
      )
      .subscribe(
        (r) => {
          this.refresh();
          this.snackBar.open(`Successfully created ${r.username}`, null, { duration: 5000 });
        },
        (err) => {
          this.snackBar.open(`Error: ${err.error.description}`, null, { duration: 5000 });
        }
      );
  }

  public edit(user: UserDetails) {
    const dialogRef = this.dialog.open(AdminUserCreateOrEditDialogComponent, { width: '500px', data: user as UserDetails });
    dialogRef
      .afterClosed()
      .pipe(
        filter((r) => r != null),
        flatMap((u: UserRequest) => {
          console.debug(`Edit Result: ${u}`);
          return this.userService.patchApiV1UserWithUserid(user.id, u);
        })
      )
      .subscribe(
        (r) => {
          this.refresh();
          this.snackBar.open(`Successfully updated ${r.username}`, null, { duration: 5000 });
        },
        (err) => {
          this.snackBar.open(`Error: ${err.error.description}`, null, { duration: 5000 });
        }
      );
  }

  public delete(userId: number) {
    if (confirm(`Do you really want to delete user (${userId})?`)) {
      this.userService.deleteApiV1UserWithUserid(userId).subscribe(
        (u: UserDetails) => {
          this.refresh();
          this.snackBar.open(`Success: ${u.username} (${u.id}) deleted`, null, { duration: 5000 });
        },
        (err) => {
          this.snackBar.open(`Error: ${err.error.description}`, null, { duration: 5000 });
        }
      );
    }
  }

  public refresh() {
    this.userService.getApiV1UserList().subscribe(
      (users: UserDetails[]) => {
        this.dataSource.data = users;
        this.dataSource.sort = this.sort;
      },
      (error) => {
        this.dataSource.data = [];
        this.dataSource.sort = this.sort;
        this.snackBar.open(`Error: ${error.error.description}`, null, { duration: 5000 });
      }
    );
  }

  ngAfterViewInit(): void {
    this.refresh();
  }

  resolveUserById(_: number, user: UserDetails) {
    return user.id;
  }

  filter() {
    this.dataSource.filter = this.filterValue.trim(); // Purposely case insensitive
  }

  private findForId(id: string) {
    this.dataSource.data.forEach((u) => {
      if (u.id === id) {
        return u;
      }
    });
    return null;
  }

  /**
   * Announce sort change state for assistive technology.
   * Direct adoption from the angular material docs.
   * We only support English everywhere, thus these announcements are in English too.
   */
  announceSortChangeForAccessibility($event: Sort) {
    if ($event.direction) {
      this.liveAnnouncer.announce(`Sorted ${$event.direction}ending on column ${$event.active}`);
    } else {
      this.liveAnnouncer.announce('Sorting cleared0');
    }
  }
}
